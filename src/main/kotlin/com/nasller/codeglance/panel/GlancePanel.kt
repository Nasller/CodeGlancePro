package com.nasller.codeglance.panel

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.nasller.codeglance.listener.GlanceListener
import com.nasller.codeglance.listener.HideScrollBarListener
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.render.Minimap
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.function.Function

class GlancePanel(project: Project, editor: EditorImpl) : AbstractGlancePanel(project,editor){
    private var mapRef = MinimapCache { MinimapRef(Minimap(this)) }
    private val glanceListener = GlanceListener(this)
    val hideScrollBarListener = HideScrollBarListener(this)
    val myPopHandler = CustomScrollBarPopup(this)
    init {
        Disposer.register(editor.disposable,this)
        editor.putUserData(CURRENT_GLANCE,this)
        scrollbar = ScrollBar(this)
        add(scrollbar)
        refresh()
    }

    fun addHideScrollBarListener(){
        if(config.hoveringToShowScrollBar && !isDisabled){
            if(!config.hideOriginalScrollBar){
                editor.scrollPane.verticalScrollBar.addMouseListener(hideScrollBarListener)
                editor.scrollPane.verticalScrollBar.addMouseMotionListener(hideScrollBarListener)
            }else{
                myVcsPanel?.addMouseListener(hideScrollBarListener)
                myVcsPanel?.addMouseMotionListener(hideScrollBarListener)
            }
            isVisible = false
        }
    }

    fun removeHideScrollBarListener(){
        hideScrollBarListener.apply {
            if(!config.hideOriginalScrollBar){
                editor.scrollPane.verticalScrollBar.removeMouseListener(this)
                editor.scrollPane.verticalScrollBar.removeMouseMotionListener(this)
            }else{
                myVcsPanel?.removeMouseListener(this)
                myVcsPanel?.removeMouseMotionListener(this)
            }
            cancel()
            showHideOriginScrollBar(true)
        }
        changeVisible()
    }

    override fun updateImgTask(updateScroll:Boolean) {
        try {
            if(updateScroll) updateScrollState()
            mapRef.get(ScaleContext.create(this)).update()
        } finally {
            renderLock.release()
            if (renderLock.dirty) {
                renderLock.clean()
                updateImage()
            }
            repaint()
        }
    }

    override fun Graphics2D.paintSelection() {
        for ((index, startByte) in editor.selectionModel.blockSelectionStarts.withIndex()) {
            val endByte = editor.selectionModel.blockSelectionEnds[index]
            val start = editor.offsetToVisualPosition(startByte)
            val end = editor.offsetToVisualPosition(endByte)
            val documentLine = getDocumentRenderLine(editor.document.getLineNumber(startByte),editor.document.getLineNumber(endByte))

            val sX = start.column
            val sY = (start.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
            val eX = end.column + 1
            val eY = (end.line + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
            if(sY >= 0 || eY >= 0) {
                setGraphics2DInfo(srcOver,editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
                // Single line is real easy
                if (start.line == end.line) {
                    fillRect(sX, sY, eX - sX, config.pixelsPerLine)
                } else {
                    // Draw the line leading in
                    fillRect(sX, sY, width - sX, config.pixelsPerLine)
                    // Then the line at the end
                    fillRect(0, eY, eX, config.pixelsPerLine)
                    if (eY + config.pixelsPerLine != sY) {
                        // And if there is anything in between, fill it in
                        fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                    }
                }
            }
        }
    }

    override fun Graphics2D.paintCaretPosition() {
        setGraphics2DInfo(srcOver,editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
        editor.caretModel.allCarets.forEach{
            val documentLine = getDocumentRenderLine(it.logicalPosition.line,it.logicalPosition.line)
            val start = (it.visualPosition.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
            if(start >= 0) fillRect(0, start, width, config.pixelsPerLine)
        }
    }

    override fun Graphics2D.paintOtherHighlight() {
        val map = lazy{ hashMapOf<String,Int>() }
        editor.markupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength) {
            val key = (it.startOffset+it.endOffset).toString()
            val layer = map.value[key]
            it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                if(layer == null || layer < it.layer){
                    drawMarkupLine(it,this,false)
                    map.value[key] = it.layer
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    override fun Graphics2D.paintErrorStripes() {
        editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength) {
            HighlightInfo.fromRangeHighlighter(it) ?.let {info ->
                it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                    drawMarkupLine(it,this,info.severity.myVal > minSeverity.myVal)
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    private fun Graphics2D.drawMarkupLine(it: RangeHighlighter, color: Color,highSeverity: Boolean){
        val fullLineError = config.showFullLineError()
        setGraphics2DInfo(if(highSeverity && fullLineError) srcOver0_6 else srcOver,color)
        val documentLine = getDocumentRenderLine(editor.offsetToLogicalPosition(it.startOffset).line, editor.offsetToLogicalPosition(it.endOffset).line)
        val start = editor.offsetToVisualPosition(it.startOffset)
        val end = editor.offsetToVisualPosition(it.endOffset)
        var sX = if (start.column > (width - minGap)) width - minGap else start.column
        val sY = (start.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
        var eX = if (start.column < (width - minGap)) end.column + 1 else width
        val eY = (end.line + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
        if(sY >= 0 || eY >= 0){
            val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(it.startOffset)
            if (sY == eY && collapsed == null) {
                if(highSeverity && eX - sX < minGap){
                    eX += minGap-(eX - sX)
                    if(eX > width) sX -= eX - width
                }
                if(highSeverity && fullLineError) {
                    fillRect(0, sY, width, config.pixelsPerLine)
                    setGraphics2DInfo(srcOver,color.brighter())
                }
                fillRect(sX, sY, eX - sX, config.pixelsPerLine)
            } else if (collapsed != null) {
                val startVis = editor.offsetToVisualPosition(collapsed.startOffset)
                val endVis = editor.offsetToVisualPosition(collapsed.endOffset)
                if(highSeverity && fullLineError) {
                    fillRect(0, sY, width, config.pixelsPerLine)
                    setGraphics2DInfo(srcOver,color.brighter())
                }
                fillRect(startVis.column, sY, endVis.column - startVis.column, config.pixelsPerLine)
            } else {
                val notEqual = eY + config.pixelsPerLine != sY
                composite = srcOver
                fillRect(sX, sY, width - sX, config.pixelsPerLine)
                if (notEqual) fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                fillRect(0, eY, eX, config.pixelsPerLine)
            }
        }
    }

    override fun getDrawImage() : BufferedImage?{
        return mapRef.get(ScaleContext.create(this)).let{
            if(!it.img.isInitialized()) {
                updateImage()
                null
            }else{
                it.img.value
            }
        }
    }

    override fun dispose() {
        mapRef.clear()
        glanceListener.dispose()
        removeHideScrollBarListener()
        editor.putUserData(CURRENT_GLANCE,null)
        super.dispose()
    }
}

private class MinimapRef(minimap: Minimap) : SoftReference<Minimap?>(minimap) {
    private var strongRef: Minimap?

    init {
        strongRef = minimap
    }

    override fun get(): Minimap? {
        val minimap = strongRef ?: super.get()
        // drop on first request
        strongRef = null
        return minimap
    }
}

private class MinimapCache(imageProvider: Function<in ScaleContext, MinimapRef>) : ScaleContext.Cache<MinimapRef?>(imageProvider) {
    fun get(ctx: ScaleContext): Minimap {
        val ref = getOrProvide(ctx)
        val image = SoftReference.dereference(ref)
        if (image != null) return image
        clear() // clear to recalculate the image
        return get(ctx) // first recalculated image will be non-null
    }
}