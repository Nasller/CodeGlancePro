package com.nasller.codeglance.panel

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.Range
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
        isVisible = !isDisabled
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

    override fun Graphics2D.paintVcs(rangeOffset: Range<Int>) {
        composite = if(config.hideOriginalScrollBar) srcOver else srcOver0_4
        editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
            if (it.isThinErrorStripeMark) it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                val start = editor.offsetToVisualPosition(it.startOffset)
                val end = editor.offsetToVisualPosition(it.endOffset)
                val documentLine = getMyRenderLine(start.line, end.line)
                val sY = start.line * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
                val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
                if (sY >= 0 || eY >= 0) {
                    color = this
                    if (sY == eY) {
                        fillRect(0, sY, width, config.pixelsPerLine)
                    } else {
                        val notEqual = eY + config.pixelsPerLine != sY
                        fillRect(0, sY, width, config.pixelsPerLine)
                        if (notEqual) fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                        fillRect(0, eY, width, config.pixelsPerLine)
                    }
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    override fun Graphics2D.paintSelection() {
        for ((index, startByte) in editor.selectionModel.blockSelectionStarts.withIndex()) {
            val endByte = editor.selectionModel.blockSelectionEnds[index]
            val start = editor.offsetToVisualPosition(startByte)
            val end = editor.offsetToVisualPosition(endByte)
            val documentLine = getMyRenderLine(start.line,end.line)

            val sX = start.column
            val sY = start.line * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
            val eX = end.column + 1
            val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
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
            val documentLine = getMyRenderLine(it.visualPosition.line,it.visualPosition.line)
            val start = it.visualPosition.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
            if(start >= 0) fillRect(0, start, width, config.pixelsPerLine)
        }
    }

    override fun Graphics2D.paintEditorMarkupModel(rangeOffset: Range<Int>) {
        val map = lazy{ hashMapOf<String,Int>() }
        editor.markupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
            it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                val key = (it.startOffset+it.endOffset).toString()
                val layer = map.value[key]
                if(layer == null || layer < it.layer){
                    drawMarkupLine(it,this,false, fullLineWithActualHighlight = false)
                    map.value[key] = it.layer
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    override fun Graphics2D.paintEditorFilterMarkupModel(rangeOffset: Range<Int>) {
        editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
            if (!it.isThinErrorStripeMark && it.layer >= HighlighterLayer.CARET_ROW && it.layer <= HighlighterLayer.SELECTION) {
                it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
                    drawMarkupLine(it, this, config.showFullLineHighlight(), it.targetArea == HighlighterTargetArea.EXACT_RANGE)
                }
            }
            return@processRangeHighlightersOverlappingWith true
        }
    }

    private fun Graphics2D.drawMarkupLine(it: RangeHighlighter, color: Color,fullLine: Boolean,fullLineWithActualHighlight: Boolean){
        val start = editor.offsetToVisualPosition(it.startOffset)
        val end = editor.offsetToVisualPosition(it.endOffset)
        val documentLine = getMyRenderLine(start.line, end.line)
        var sX = if (start.column > (width - minGap)) width - minGap else start.column
        val sY = start.line  * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
        var eX = if (start.column < (width - minGap)) end.column + 1 else width
        val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
        if(sY >= 0 || eY >= 0){
            setGraphics2DInfo(if(fullLine && fullLineWithActualHighlight) srcOver0_6 else srcOver,color)
            val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(it.startOffset)
            if (sY == eY && collapsed == null) {
                if(fullLineWithActualHighlight && eX - sX < minGap){
                    eX += minGap-(eX - sX)
                    if(eX > width) sX -= eX - width
                }
                drawMarkOneLine(fullLine, fullLineWithActualHighlight, color, sY, sX, eX)
            } else if (collapsed != null) {
                val startVis = editor.offsetToVisualPosition(collapsed.startOffset)
                val endVis = editor.offsetToVisualPosition(collapsed.endOffset)
                drawMarkOneLine(fullLine,fullLineWithActualHighlight, color, sY,startVis.column,endVis.column)
            } else {
                val notEqual = eY + config.pixelsPerLine != sY
                fillRect(if(fullLine) 0 else sX, sY, if(fullLine) width else width - sX, config.pixelsPerLine)
                if (notEqual) fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
                fillRect(0, eY, if(fullLine) width else eX, config.pixelsPerLine)
            }
        }
    }

    private fun Graphics2D.drawMarkOneLine(fullLine: Boolean, fullLineWithActualHighlight: Boolean, color: Color, sY: Int, sX: Int, eX: Int) {
        if (fullLine && fullLineWithActualHighlight) {
            fillRect(0, sY, width, config.pixelsPerLine)
            setGraphics2DInfo(srcOver, color.brighter())
            fillRect(sX, sY, eX - sX, config.pixelsPerLine)
        } else if (fullLine) {
            fillRect(0, sY, width, config.pixelsPerLine)
        } else {
            fillRect(sX, sY, eX - sX, config.pixelsPerLine)
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
        super.dispose()
        glanceListener.dispose()
        removeHideScrollBarListener()
        mapRef.clear()
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