package com.nasller.codeglance.panel

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.CodeGlancePlugin
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

sealed class AbstractGlancePanel(val project: Project, textEditor: TextEditor) : JPanel(),Disposable {
    val editor = textEditor.editor as EditorEx
    val config = ConfigInstance.state
    val scrollState = ScrollState()
    val trackerManager = if(CodeGlancePlugin.MODULE_VCS)LineStatusTrackerManager.getInstance(project) else null
    val changeListManager = if(CodeGlancePlugin.MODULE_VCS)ChangeListManager.getInstance(project) else null
    val fileEditorManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
    protected val renderLock = DirtyLock()
    private val panelParent = textEditor.editor.component as JPanel
    val isDisabled: Boolean
        get() = config.disabled || editor.virtualFile.length > PersistentFSConstants.getMaxIntellisenseFileSize() ||
                editor.document.lineCount > config.maxLinesCount
    private var buf: BufferedImage? = null
    var scrollbar: ScrollBar? = null
    var myVcsPanel:MyVcsPanel? = null

    init {
        isOpaque = false
        panelParent.isOpaque = false
        layout = BorderLayout()
    }

    fun refresh(refreshImage:Boolean = true) {
        updateSize()
        if(refreshImage) updateImage(false,::updateScrollState)
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

    fun updateImage(directUpdate: Boolean = false, consumer: (() -> Unit)? = null) {
        if (shouldNotUpdate() || !renderLock.acquire()) return
        val runnable = {
            consumer?.invoke()
            updateImgTask()
        }
        if(directUpdate) runnable()
        else ApplicationManager.getApplication().invokeLater(runnable)
    }

    fun shouldNotUpdate() = isDisabled || project.isDisposed || !isVisible

    fun updateScrollState(){
        scrollState.computeDimensions(this)
        scrollState.recomputeVisible(editor.scrollingModel.visibleArea)
    }

    protected abstract fun updateImgTask()

    abstract fun paintVcs(g: Graphics2D,notPaint:Boolean)

    abstract fun paintSelection(g: Graphics2D, startByte: Int, endByte: Int)

    abstract fun paintCaretPosition(g: Graphics2D): MutableList<VisualPosition>

    abstract fun paintOtherHighlight(g: Graphics2D,allCarets:List<VisualPosition>)

    abstract fun paintErrorStripes(g: Graphics2D,allCarets:List<VisualPosition>)

    private fun paintCaretsOrSelections(g: Graphics2D) : List<VisualPosition> {
        return if(editor.selectionModel.hasSelection()){
            for ((index, start) in editor.selectionModel.blockSelectionStarts.withIndex()) {
                paintSelection(g, start, editor.selectionModel.blockSelectionEnds[index])
            }
            emptyList()
        }else{
            paintCaretPosition(g)
        }
    }

    fun getDocumentRenderLine(lineStart:Int,lineEnd:Int):Pair<Int,Int>{
        var startAdd = 0
        var endAdd = 0
        editor.foldingModel.allFoldRegions.filter{ !it.isExpanded && it is CustomFoldRegionImpl &&
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

    override fun paint(gfx: Graphics) {
        if(shouldNotUpdate()) return
        if (renderLock.locked) {
            paintLast(gfx as Graphics2D)
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
        val allCarets = paintCaretsOrSelections(graphics2D)
        paintOtherHighlight(graphics2D,allCarets)
        paintErrorStripes(graphics2D,allCarets)
        graphics2D.composite = srcOver0_8
        graphics2D.drawImage(buf, 0, 0, null)
        scrollbar?.paint(graphics2D)
    }

    private fun paintLast(gfx: Graphics2D) {
        buf?.apply{
            gfx.composite = srcOver0_8
            gfx.drawImage(this,0, 0, width, height, 0, 0, width, height,null)
        }
        paintVcs(gfx,config.hideOriginalScrollBar)
        val allCarets = paintCaretsOrSelections(gfx)
        paintOtherHighlight(gfx,allCarets)
        paintErrorStripes(gfx,allCarets)
        scrollbar?.paint(gfx)
    }

    fun changeOriginScrollBarWidth(){
        if (config.hideOriginalScrollBar && !isDisabled && isVisible) {
            myVcsPanel?.apply { preferredSize = Dimension(MyVcsPanel.vcsWidth, preferredSize.height) }
            editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(0, preferredSize.height) }
        }else{
            myVcsPanel?.apply { preferredSize = Dimension(0, preferredSize.height) }
            editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(originalScrollbarWidth, preferredSize.height) }
        }
    }

    abstract fun getDrawImage() : BufferedImage?

    override fun dispose() {
        scrollbar?.let {
            it.dispose()
            remove(it)
        }
        myVcsPanel?.dispose()
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
        val CURRENT_GLANCE = Key<GlancePanel>("CURRENT_GLANCE")
    }
}