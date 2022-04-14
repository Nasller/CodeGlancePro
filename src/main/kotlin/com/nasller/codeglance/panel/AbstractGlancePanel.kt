package com.nasller.codeglance.panel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ReadTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.PersistentFSConstants
import com.nasller.codeglance.CodeGlancePlugin
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.Config
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

sealed class AbstractGlancePanel(val project: Project, textEditor: TextEditor,private val panelParent: JPanel) : JPanel(),
    SettingsChangeListener,Disposable {
    val editor = textEditor.editor as EditorEx
    var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
    val config: Config = ConfigInstance.state
    val scrollState = ScrollState()
    val trackerManager = LineStatusTrackerManager.getInstance(project)
    val changeListManager: ChangeListManagerImpl = ChangeListManagerImpl.getInstanceImpl(project)
    val fileEditorManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    protected val renderLock = DirtyLock()
    private val updateTask: ReadTask = object :ReadTask() {
        override fun onCanceled(indicator: ProgressIndicator) {
            renderLock.release()
            renderLock.clean()
            updateImageSoon()
        }

        override fun computeInReadAction(indicator: ProgressIndicator) {
            this@AbstractGlancePanel.computeInReadAction(indicator)
        }
    }
    private val isDisabled: Boolean
        get() = config.disabled || editor.document.textLength > PersistentFSConstants.getMaxIntellisenseFileSize() ||
                editor.document.lineCount < config.minLineCount
    private var buf: BufferedImage? = null
    protected var scrollbar:ScrollBar? = null
    var myVcsPanel:MyVcsPanel? = null

    init {
        isOpaque = false
        layout = BorderLayout()
    }

    fun refresh() {
        updateSize()
        updateImage()
        revalidate()
    }

    /**
     * Adjusts the panels size to be a percentage of the total window
     */
    private fun updateSize() {
        preferredSize = if (isDisabled) {
            Dimension(0, 0)
        } else {
            var calWidth = panelParent.width / 10
            calWidth = if(fileEditorManagerEx.isInSplitter && calWidth < config.width){
                if (calWidth < 20) 20 else calWidth
            }else config.width
            Dimension(calWidth, 0)
        }
    }

    fun updateImageSoon() = ApplicationManager.getApplication().invokeLater(this::updateImage)

    /**
     * Fires off a new task to the worker thread. This should only be called from the ui thread.
     */
    fun updateImage() {
        if (isDisabled) return
        if (project.isDisposed) return
        if (!renderLock.acquire()) return
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(updateTask)
    }

    private fun paintLast(gfx: Graphics?) {
        val g = gfx as Graphics2D
        buf?.run{ g.drawImage(this,0, 0, width, height, 0, 0, width, height,null) }
        paintSelections(g)
        if(config.hideOriginalScrollBar) myVcsPanel?.repaint() else paintVcs(g)
        scrollbar!!.paint(gfx)
    }

    abstract fun computeInReadAction(indicator: ProgressIndicator)

    abstract fun paintVcs(g: Graphics2D)

    abstract fun paintSelection(g: Graphics2D, startByte: Int, endByte: Int)

    abstract fun paintCaretPosition(g: Graphics2D)

    abstract fun paintOtherHighlight(g: Graphics2D)

    abstract fun paintErrorStripes(g: Graphics2D)

    private fun paintSelections(g: Graphics2D) {
        if(editor.selectionModel.hasSelection()){
            for ((index, start) in editor.selectionModel.blockSelectionStarts.withIndex()) {
                paintSelection(g, start, editor.selectionModel.blockSelectionEnds[index])
            }
        }else{
            paintCaretPosition(g)
        }
    }

    protected fun getDocumentRenderLine(lineStart:Int,lineEnd:Int):Pair<Int,Int>{
        var startAdd = 0
        var endAdd = 0
        editor.foldingModel.allFoldRegions.filter{ CodeGlancePlugin.isCustomFoldRegionImpl(it) && !it.isExpanded &&
                it.startOffset >= 0 && it.endOffset >= 0}.forEach{
            val start = it.document.getLineNumber(it.startOffset)
            val end = it.document.getLineNumber(it.endOffset)
            val i = end - start
            if(lineStart < start && end < lineEnd){
                endAdd += i
            }else if(end < lineEnd){
                startAdd += i
                endAdd += i
            }
        }
        return Pair(startAdd,endAdd)
    }

    protected fun drawMarkupLine(it: RangeHighlighter, g: Graphics2D,color: Color, compensateLine:Boolean){
        g.color = color
        val documentLine = getDocumentRenderLine(editor.offsetToLogicalPosition(it.startOffset).line, editor.offsetToLogicalPosition(it.endOffset).line)
        val start = editor.offsetToVisualPosition(it.startOffset)
        val end = editor.offsetToVisualPosition(it.endOffset)
        var sX = if (start.column > (width - minGap)) width - minGap else start.column
        val sY = (start.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
        var eX = if (start.column < (width - minGap)) end.column + 1 else width
        val eY = (end.line + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
        val collapsed = editor.foldingModel.isOffsetCollapsed(it.startOffset)
        if (sY == eY && !collapsed) {
            val gap = eX - sX
            if(compensateLine && gap < minGap){
                eX += minGap-gap
                if(eX > width) sX -= eX - width
            }
            g.fillRect(sX, sY, eX - sX, config.pixelsPerLine)
        } else if(collapsed){
            g.fillRect(0, sY, width / 2, config.pixelsPerLine)
        } else {
            g.fillRect(sX, sY, width - sX, config.pixelsPerLine)
            g.fillRect(0, eY, eX, config.pixelsPerLine)
            if (eY + config.pixelsPerLine != sY) {
                g.fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
            }
        }
    }

    override fun paint(gfx: Graphics?) {
        if (renderLock.locked) {
            paintLast(gfx)
            return
        }
        val img = getDrawImage() ?: return
        if (buf == null || buf?.width!! < width || buf?.height!! < height) {
            buf = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        }
        val g = buf!!.createGraphics()
        g.composite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
        g.fillRect(0, 0, width, height)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
        if (editor.document.textLength != 0) {
            g.drawImage(img, 0, 0, scrollState.documentWidth, scrollState.drawHeight,
                0, scrollState.visibleStart, scrollState.documentWidth, scrollState.visibleEnd, null)
        }
        val graphics2D = gfx as Graphics2D
        if(config.hideOriginalScrollBar) myVcsPanel?.repaint() else paintVcs(graphics2D)
        paintSelections(graphics2D)
        paintOtherHighlight(graphics2D)
        paintErrorStripes(graphics2D)
        graphics2D.composite = srcOver0_8
        graphics2D.drawImage(buf, 0, 0, null)
        scrollbar?.paint(graphics2D)
    }

    override fun onRefreshChanged() {
        refresh()
        changeOriginScrollBarWidth()
    }

    fun changeOriginScrollBarWidth(){
        if (config.hideOriginalScrollBar && !config.disabled) {
            editor.scrollPane.verticalScrollBar.run {
                this.preferredSize = Dimension(0, this.preferredSize.height)
            }
        }else{
            editor.scrollPane.verticalScrollBar.run {
                this.preferredSize = Dimension(originalScrollbarWidth, this.preferredSize.height)
            }
        }
    }

    abstract fun getDrawImage() : BufferedImage?

    override fun dispose() {
        scrollbar?.let {remove(it)}
    }

    companion object{
        const val minGap = 15
        const val minWidth = 50
        const val maxWidth = 250
        val srcOver0_4: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f)
        val srcOver0_8: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
        val srcOver: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
    }
}