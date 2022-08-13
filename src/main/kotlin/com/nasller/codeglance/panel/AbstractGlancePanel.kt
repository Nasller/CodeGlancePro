package com.nasller.codeglance.panel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.Range
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.EditorPanelInjector
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

sealed class AbstractGlancePanel(val project: Project,val editor: EditorImpl):JPanel(),Disposable {
    val config = ConfigInstance.state
    val scrollState = ScrollState()
    val fileEditorManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    val myRangeList: MutableList<Pair<Int, Range<Int>>> = ContainerUtil.createLockFreeCopyOnWriteList()
    val isDisabled: Boolean
        get() = config.disabled || editor.document.lineCount > config.maxLinesCount
    protected val renderLock = DirtyLock()
    private var buf: BufferedImage? = null
    var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
    var scrollbar: ScrollBar? = null
    var myVcsPanel: MyVcsPanel? = null

    init {
        isOpaque = false
        editor.component.isOpaque = false
        layout = BorderLayout()
        isVisible = !isDisabled
    }

    fun refresh(refreshImage:Boolean = true,directUpdate:Boolean = false) {
        updateSize()
        if(refreshImage) updateImage(directUpdate, updateScroll = true)
        else repaint()
        revalidate()
    }

    private fun updateSize() {
        preferredSize = run{
            var calWidth = editor.component.width / 12
            calWidth = if(fileEditorManagerEx.isInSplitter && calWidth < config.width){
                if (calWidth < 20) 20 else calWidth
            }else config.width
            Dimension(calWidth, 0)
        }
    }

    fun updateImage(directUpdate: Boolean = false, updateScroll:Boolean = false) =
        if (checkVisible() && renderLock.acquire()) {
            if (directUpdate) updateImgTask(updateScroll)
            else ApplicationManager.getApplication().invokeLater{ updateImgTask(updateScroll) }
        } else Unit

    fun checkVisible() = !((!config.hoveringToShowScrollBar && !isVisible) || editor.isDisposed)

    fun updateScrollState() = scrollState.run{
        computeDimensions()
        recomputeVisible(editor.scrollingModel.visibleArea)
    }

    protected abstract fun updateImgTask(updateScroll:Boolean = false)

    abstract fun Graphics2D.paintVcs(rangeOffset: Range<Int>)

    abstract fun Graphics2D.paintSelection()

    abstract fun Graphics2D.paintCaretPosition()

    abstract fun Graphics2D.paintEditorMarkupModel(rangeOffset: Range<Int>)

    abstract fun Graphics2D.paintEditorFilterMarkupModel(rangeOffset: Range<Int>)

    fun getMyRenderLine(lineStart:Int, lineEnd:Int):Pair<Int,Int>{
        var startAdd = 0
        var endAdd = 0
        myRangeList.forEach {
            if (it.first in (lineStart + 1) until lineEnd) {
                endAdd += it.second.to - it.second.from
            } else if (it.first < lineStart) {
                val i = it.second.to - it.second.from
                startAdd += i
                endAdd += i
            }
        }
        return startAdd to endAdd
    }

    fun getMyRenderVisualLine(y:Int):Int{
        var minus = 0
        for (pair in myRangeList) {
            if (y in pair.second.from .. pair.second.to) {
                return pair.first
            } else if (pair.second.to < y) {
                minus += pair.second.to - pair.second.from
            }
        }
        return (y - minus) / config.pixelsPerLine
    }

    override fun paint(gfx: Graphics) {
        if (renderLock.locked) return paintLast(gfx as Graphics2D)
        val img = getDrawImage() ?: return
        if (buf == null || buf?.width!! < width || buf?.height!! < height) {
            buf = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        }
        UIUtil.useSafely(buf!!.graphics){
            it.composite = CLEAR
            it.fillRect(0, 0, width, height)
            it.composite = srcOver
            if (editor.document.textLength != 0) {
                it.drawImage(img, 0, 0, img.width, scrollState.drawHeight,
                    0, scrollState.visibleStart, img.width, scrollState.visibleEnd, null)
            }
        }
        with(gfx as Graphics2D){
            paintSomething()
            composite = srcOver0_8
            drawImage(buf, 0, 0, null)
            scrollbar?.paint(this)
        }
    }

    private fun paintLast(gfx: Graphics2D) {
        buf?.apply{
            gfx.composite = srcOver0_8
            gfx.drawImage(this,0, 0, width, height, 0, 0, width, height,null)
        }
        gfx.paintSomething()
        scrollbar?.paint(gfx)
    }

    private fun Graphics2D.paintSomething(){
        val rangeOffset = getVisibleRangeOffset()
        if(!config.hideOriginalScrollBar) paintVcs(rangeOffset)
        if(editor.selectionModel.hasSelection()) paintSelection()
        else paintCaretPosition()
        paintEditorFilterMarkupModel(rangeOffset)
        paintEditorMarkupModel(rangeOffset)
    }

    fun Graphics2D.setGraphics2DInfo(al: AlphaComposite,col: Color?){
        composite = al
        color = col
    }

    fun getVisibleRangeOffset():Range<Int>{
        var startOffset = 0
        var endOffset = editor.document.textLength
        if(scrollState.visibleStart > 0) {
            val offset = editor.visualLineStartOffset(fitLineToEditor(editor, getMyRenderVisualLine(scrollState.visibleStart))) - 1
            startOffset = if(offset > 0) offset else 0
        }
        if(scrollState.visibleEnd > 0){
            val offset = EditorUtil.getVisualLineEndOffset(editor,fitLineToEditor(editor,getMyRenderVisualLine(scrollState.visibleEnd))) + 1
            endOffset = if(offset < endOffset) offset else endOffset
        }
        return Range(startOffset,endOffset)
    }

    fun changeOriginScrollBarWidth(control:Boolean = true){
        if (config.hideOriginalScrollBar && control && (!isDisabled || isVisible)) {
            myVcsPanel?.apply { isVisible = true }
            editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(0, preferredSize.height) }
        }else{
            myVcsPanel?.apply { isVisible = false }
            editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(originalScrollbarWidth, preferredSize.height) }
        }
    }

    abstract fun getDrawImage() : BufferedImage?

    override fun dispose() {
        editor.component.remove(if(this.parent is EditorPanelInjector.MyPanel) this.parent else this)
        editor.putUserData(CURRENT_GLANCE,null)
        scrollbar?.dispose()
        myVcsPanel?.dispose()
    }

    inner class RangeHighlightColor(val startOffset: Int,val endOffset: Int, val color: Color, val fullLine: Boolean, val fullLineWithActualHighlight: Boolean){
        constructor(it:RangeHighlighterEx,color: Color) : this(it.startOffset,it.endOffset,color,false,false)
        constructor(it:RangeHighlighterEx,color: Color,fullLine: Boolean) : this(it.startOffset,it.endOffset,color,fullLine,it.targetArea == HighlighterTargetArea.EXACT_RANGE)
        val startVis by lazy{ editor.offsetToVisualPosition(startOffset) }
        val endVis by lazy{ editor.offsetToVisualPosition(endOffset) }
    }

    companion object{
        const val minGap = 15
        const val minWidth = 50
        const val maxWidth = 250
        val CLEAR: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
        val srcOver0_4: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f)
        val srcOver0_6: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.60f)
        val srcOver0_8: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
        val srcOver: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
        val CURRENT_GLANCE = Key<GlancePanel>("CURRENT_GLANCE")

        fun fitLineToEditor(editor: EditorImpl, visualLine: Int): Int {
            val lineCount = editor.visibleLineCount
            var shift = 0
            if (visualLine >= lineCount - 1) {
                val sequence = editor.document.charsSequence
                shift = if (sequence.isEmpty()) 0 else if (sequence[sequence.length - 1] == '\n') 1 else 0
            }
            return 0.coerceAtLeast((lineCount - shift).coerceAtMost(visualLine))
        }
    }
}