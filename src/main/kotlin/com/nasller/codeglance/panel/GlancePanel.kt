package com.nasller.codeglance.panel

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.nasller.codeglance.listener.GlanceListener
import com.nasller.codeglance.listener.HideScrollBarListener
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.render.Minimap
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.function.Function

class GlancePanel(project: Project, textEditor: TextEditor) : AbstractGlancePanel(project,textEditor){
    private var mapRef = MinimapCache { MinimapRef(Minimap(this,scrollState)) }
    private val glanceListener = GlanceListener(this)
    val hideScrollBarListener = HideScrollBarListener(this)
    val myPopHandler = CustomScrollBarPopup(this)
    init {
        Disposer.register(textEditor,this)
        editor.putUserData(CURRENT_GLANCE,this)
        scrollbar = ScrollBar(this)
        add(scrollbar)
        refresh()
    }

    fun addHideScrollBarListener(){
        if(config.hoveringToShowScrollBar){
            ApplicationManager.getApplication().invokeLater { isVisible = false }
            if(!config.hideOriginalScrollBar){
                editor.scrollPane.verticalScrollBar.addMouseListener(hideScrollBarListener)
                editor.scrollPane.verticalScrollBar.addMouseMotionListener(hideScrollBarListener)
            }else{
                myVcsPanel?.addMouseListener(hideScrollBarListener)
                myVcsPanel?.addMouseMotionListener(hideScrollBarListener)
            }
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
        isVisible = true
    }

    override fun updateImgTask() {
        try {
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

    override fun paintSelection(g: Graphics2D, startByte: Int, endByte: Int) {
        val start = editor.offsetToVisualPosition(startByte)
        val end = editor.offsetToVisualPosition(endByte)
        val documentLine = getDocumentRenderLine(editor.document.getLineNumber(startByte),editor.document.getLineNumber(endByte))

        val sX = start.column
        val sY = (start.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
        val eX = end.column + 1
        val eY = (end.line + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
        if(sY >= 0 || eY >= 0) {
            g.setGraphics2DInfo(srcOver,editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
            // Single line is real easy
            if (start.line == end.line) {
                g.fillRect(sX, sY, eX - sX, config.pixelsPerLine)
            } else {
                // Draw the line leading in
                g.fillRect(sX, sY, width - sX, config.pixelsPerLine)
                // Then the line at the end
                g.fillRect(0, eY, eX, config.pixelsPerLine)
                if (eY + config.pixelsPerLine != sY) {
                    // And if there is anything in between, fill it in
                    g.fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                }
            }
        }
    }

    override fun paintCaretPosition(g: Graphics2D) {
        g.setGraphics2DInfo(srcOver,editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
        editor.caretModel.allCarets.forEach{
            val documentLine = getDocumentRenderLine(it.logicalPosition.line,it.logicalPosition.line)
            val start = (it.visualPosition.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
            if(start >= 0) g.fillRect(0, start, width, config.pixelsPerLine)
        }
    }

    override fun paintOtherHighlight(g: Graphics2D) {
        val map = lazy{ hashMapOf<String,Int>() }
        editor.markupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength) {
            val key = (it.startOffset+it.endOffset).toString()
            val layer = map.value[key]
            it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                if(layer == null || layer < it.layer){
                    drawMarkupLine(it,g,this,false)
                    map.value[key] = it.layer
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    override fun paintErrorStripes(g: Graphics2D) {
        editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength) {
            HighlightInfo.fromRangeHighlighter(it) ?.let {info ->
                it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                    drawMarkupLine(it, g,this,info.severity.myVal > minSeverity.myVal)
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    private fun drawMarkupLine(it: RangeHighlighter, g: Graphics2D, color: Color,highSeverity: Boolean){
        g.setGraphics2DInfo(if(highSeverity && config.showFullLineError) srcOver0_6 else srcOver,color)
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
                if(highSeverity && config.showFullLineError) {
                    g.fillRect(0, sY, width, config.pixelsPerLine)
                    g.setGraphics2DInfo(srcOver,g.color.brighter())
                }
                g.fillRect(sX, sY, eX - sX, config.pixelsPerLine)
            } else if (collapsed != null) {
                val startVis = editor.offsetToVisualPosition(collapsed.startOffset)
                val endVis = editor.offsetToVisualPosition(collapsed.endOffset)
                if(highSeverity && config.showFullLineError) {
                    g.fillRect(0, sY, width, config.pixelsPerLine)
                    g.setGraphics2DInfo(srcOver,g.color.brighter())
                }
                g.fillRect(startVis.column, sY, endVis.column - startVis.column, config.pixelsPerLine)
            } else {
                val notEqual = eY + config.pixelsPerLine != sY
                g.composite = srcOver
                g.fillRect(sX, sY, width - sX, config.pixelsPerLine)
                if (notEqual) g.fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                g.fillRect(0, eY, eX, config.pixelsPerLine)
            }
        }
    }

    private fun Graphics2D.setGraphics2DInfo(al: AlphaComposite,col: Color?){
        composite = al
        color = col
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