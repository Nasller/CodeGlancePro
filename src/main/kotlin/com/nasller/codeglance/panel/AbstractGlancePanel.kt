package com.nasller.codeglance.panel

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ReadTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.CodeGlancePlugin
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.Config
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

sealed class AbstractGlancePanel(val project: Project, textEditor: TextEditor,private val panelParent: JPanel) : JPanel(),Disposable {
    val editor = textEditor.editor as EditorEx
    var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
    val config: Config = ConfigInstance.state
    val scrollState = ScrollState()
    val trackerManager = LineStatusTrackerManager.getInstance(project)
    val changeListManager: ChangeListManager = ChangeListManager.getInstance(project)
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
    val isDisabled: Boolean
        get() = config.disabled || editor.document.textLength > PersistentFSConstants.getMaxIntellisenseFileSize() ||
                editor.document.lineCount > config.maxLinesCount
    private var buf: BufferedImage? = null
    var scrollbar:ScrollBar? = null
    var myVcsPanel:MyVcsPanel? = null

    init {
        isOpaque = false
        panelParent.isOpaque = false
        layout = BorderLayout()
    }

    fun refresh(refreshImage:Boolean = true) {
        updateSize()
        if(refreshImage) updateImage()
        else repaint()
        revalidate()
    }

    private fun updateSize() {
        preferredSize = if (isDisabled) {
            Dimension(0, 0)
        } else {
            var calWidth = panelParent.width / 12
            calWidth = if(fileEditorManagerEx.isInSplitter && calWidth < config.width){
                if (calWidth < 20) 20 else calWidth
            }else config.width
            Dimension(calWidth, 0)
        }
    }

    fun updateImageSoon() = ApplicationManager.getApplication().invokeLater(this::updateImage)

    fun updateImage() {
        if (isDisabled) return
        if (!renderLock.acquire()) return
        if (project.isDisposed) return
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(updateTask)
    }

    private fun paintLast(gfx: Graphics?) {
        val g = gfx as Graphics2D
        buf?.run{
            g.composite = srcOver0_8
            g.drawImage(this,0, 0, width, height, 0, 0, width, height,null)
        }
        paintVcs(g,config.hideOriginalScrollBar)
        paintSelections(g)
        paintOtherHighlight(g)
        paintErrorStripes(g)
        scrollbar?.paint(gfx)
    }

    abstract fun computeInReadAction(indicator: ProgressIndicator)

    abstract fun paintVcs(g: Graphics2D,notPaint:Boolean)

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

    fun getDocumentRenderLine(lineStart:Int,lineEnd:Int):Pair<Int,Int>{
        var startAdd = 0
        var endAdd = 0
        editor.foldingModel.allFoldRegions.filter{ !it.isExpanded && CodeGlancePlugin.isCustomFoldRegionImpl(it) &&
                it.startOffset >= 0 && it.endOffset >= 0}.forEach {
            val start = it.document.getLineNumber(it.startOffset)
            val end = it.document.getLineNumber(it.endOffset)
            val i = end - start
            if (lineStart < start && end < lineEnd) {
                endAdd += i
            } else if (end < lineEnd) {
                startAdd += i
                endAdd += i
            }
        }
        return startAdd to endAdd
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
        UIUtil.useSafely(buf!!.graphics){
            it.composite = CLEAR
            it.fillRect(0, 0, width, height)
            it.composite = srcOver
            if (editor.document.textLength != 0) {
                it.drawImage(img, 0, 0, img.width, scrollState.drawHeight,
                    0, scrollState.visibleStart, img.width, scrollState.visibleEnd, null)
            }
        }
        val graphics2D = gfx as Graphics2D
        paintVcs(graphics2D,config.hideOriginalScrollBar)
        paintSelections(graphics2D)
        paintOtherHighlight(graphics2D)
        paintErrorStripes(graphics2D)
        graphics2D.composite = srcOver0_8
        graphics2D.drawImage(buf, 0, 0, null)
        scrollbar?.paint(graphics2D)
    }

    fun changeOriginScrollBarWidth(){
        if (config.hideOriginalScrollBar && !isDisabled) {
            myVcsPanel?.run { this.preferredSize = Dimension(MyVcsPanel.vcsWidth, this.preferredSize.height) }
            editor.scrollPane.verticalScrollBar.run { this.preferredSize = Dimension(0, this.preferredSize.height) }
        }else{
            myVcsPanel?.run { this.preferredSize = Dimension(0, this.preferredSize.height) }
            editor.scrollPane.verticalScrollBar.run { this.preferredSize = Dimension(originalScrollbarWidth, this.preferredSize.height) }
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
        val CLEAR: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
        val srcOver0_4: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f)
        val srcOver0_8: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
        val srcOver: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
        val minSeverity = ObjectUtils.notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.DO_NOT_SHOW).severity
    }
}