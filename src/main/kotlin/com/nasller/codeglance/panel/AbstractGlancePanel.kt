package com.nasller.codeglance.panel

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import com.nasller.codeglance.panel.vcs.VcsRenderService
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel

sealed class AbstractGlancePanel(val project: Project, textEditor: TextEditor) : JPanel(),Disposable {
    val editor = textEditor.editor as EditorEx
    val config = ConfigInstance.state
    val scrollState = ScrollState()
    val vcsRenderService: VcsRenderService? = project.getService(VcsRenderService::class.java)
    val fileEditorManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
    protected val renderLock = DirtyLock()
    private val panelParent = textEditor.editor.component as JPanel
    val isDisabled: Boolean
        get() = config.disabled || editor.document.lineCount > config.maxLinesCount
    private var buf: BufferedImage? = null
    var scrollbar: ScrollBar? = null
    var myVcsPanel: MyVcsPanel? = null

    init {
        isOpaque = false
        panelParent.isOpaque = false
        layout = BorderLayout()
        changeVisible()
    }

    fun refresh(refreshImage:Boolean = true,directUpdate:Boolean = false) {
        updateSize()
        if(refreshImage) updateImage(directUpdate, updateScroll = true)
        else repaint()
        revalidate()
    }

    private fun updateSize() {
        preferredSize = run{
            var calWidth = panelParent.width / 12
            calWidth = if(fileEditorManagerEx.isInSplitter && calWidth < config.width){
                if (calWidth < 20) 20 else calWidth
            }else config.width
            Dimension(calWidth, 0)
        }
    }

    fun updateImage(directUpdate: Boolean = false, updateScroll:Boolean = false) =
        if (shouldUpdate() && renderLock.acquire()) {
            if (directUpdate) updateImgTask(updateScroll)
            else ApplicationManager.getApplication().invokeLater{ updateImgTask(updateScroll) }
        } else Unit

    fun shouldUpdate() = !((!config.hoveringToShowScrollBar && !isVisible) || project.isDisposed)

    fun changeVisible(){
        isVisible = !isDisabled
    }

    fun updateScrollState(){
        scrollState.computeDimensions(this)
        scrollState.recomputeVisible(editor.scrollingModel.visibleArea)
    }

    protected abstract fun updateImgTask(updateScroll:Boolean = false)

    abstract fun Graphics2D.paintSelection()

    abstract fun Graphics2D.paintCaretPosition()

    abstract fun Graphics2D.paintOtherHighlight()

    abstract fun Graphics2D.paintErrorStripes()

    fun getDocumentRenderLine(lineStart:Int,lineEnd:Int):Pair<Int,Int>{
        var startAdd = 0
        var endAdd = 0
        editor.foldingModel.allFoldRegions.filter{ !it.isExpanded && it is CustomFoldRegionImpl }.forEach {
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
        vcsRenderService?.paintVcs(this@AbstractGlancePanel,this,config.hideOriginalScrollBar)
        if(editor.selectionModel.hasSelection()) paintSelection()
        else paintCaretPosition()
        paintOtherHighlight()
        paintErrorStripes()
    }

    protected fun Graphics2D.setGraphics2DInfo(al: AlphaComposite,col: Color?){
        composite = al
        color = col
    }

    fun changeOriginScrollBarWidth(){
        if (config.hideOriginalScrollBar && isVisible) {
            myVcsPanel?.apply { isVisible = true }
            editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(0, preferredSize.height) }
        }else{
            myVcsPanel?.apply { isVisible = false }
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
        val srcOver0_6: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.60f)
        val srcOver0_8: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
        val srcOver: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
        val minSeverity = ObjectUtils.notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.DO_NOT_SHOW).severity
        val CURRENT_GLANCE = Key<GlancePanel>("CURRENT_GLANCE")
    }
}