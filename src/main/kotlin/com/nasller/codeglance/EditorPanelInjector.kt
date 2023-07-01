package com.nasller.codeglance

import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

class EditorPanelInjector(private val project: Project) : FileOpenedSyncListener,SettingsChangeListener,LafManagerListener,Disposable {
    private val logger = Logger.getInstance(javaClass)
    private var isFirstSetup = true
    init {
        CodeGlancePlugin.projectMap[project] = this
        ApplicationManager.getApplication().messageBus.connect(this).apply {
            subscribe(LafManagerListener.TOPIC, this@EditorPanelInjector)
            subscribe(SettingsChangeListener.TOPIC, this@EditorPanelInjector)
        }
    }

    /** FileOpenedSyncListener */
    override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
        val extension = file.fileType.defaultExtension
        if(extension.isNotBlank() && CodeGlanceConfigService.getConfig().disableLanguageSuffix
            .split(",").toSet().contains(extension)) return
        source.getAllEditors(file).runAllEditors{ info: EditorInfo ->
            val editor = info.editor as? EditorImpl
            val layout = (editor?.component as? JPanel)?.layout
            if (layout is BorderLayout && layout.getLayoutComponent(info.place) == null) {
                val myPanel = getMyPanel(editor,info.place)
                editor.component.add(myPanel, info.place)
                myPanel.applyGlancePanel { changeOriginScrollBarWidth() }
            }
        }
    }

    /** SettingsChangeListener */
    override fun onGlobalChanged() {
        val config = CodeGlanceConfigService.getConfig()
        val disable = config.disableLanguageSuffix.split(",").toSet()
        processAllGlanceEditor { component, info ->
            if(component != null) info.editor.component.remove(component)
            val oldGlancePanel = component?.applyGlancePanel { Disposer.dispose(this) }
            val extension = info.editor.virtualFile.fileType.defaultExtension
            if(extension.isNotBlank() && disable.contains(extension)) {
                oldGlancePanel?.changeOriginScrollBarWidth(false)
            } else {
                val myPanel = getMyPanel(info.editor,info.place)
                info.editor.component.add(myPanel, info.place)
                myPanel.applyGlancePanel {
                    oldGlancePanel?.let{ glancePanel -> originalScrollbarWidth = glancePanel.originalScrollbarWidth }
                    changeOriginScrollBarWidth()
                    updateImage()
                }
            }
        }
    }

    /** LafManagerListener */
    override fun lookAndFeelChanged(source: LafManager) = if(isFirstSetup) isFirstSetup = false else {
        processAllGlanceEditor { component, _ -> component?.applyGlancePanel{ refresh() } }
    }

    private fun processAllGlanceEditor(action: (component:Component?,EditorInfo)->Unit){
        try {
            FileEditorManager.getInstance(project).allEditors.runAllEditors {info: EditorInfo ->
                val layout = (info.editor.component as? JPanel)?.layout
                if (layout is BorderLayout) {
                    action((layout.getLayoutComponent(BorderLayout.LINE_END) ?:
                    layout.getLayoutComponent(BorderLayout.LINE_START)), info)
                }
            }
        }catch (e:Exception){
            logger.error(e)
        }
    }

    private fun Array<FileEditor>.runAllEditors(withTextEditor: (EditorInfo) -> Unit) {
        val where = if (CodeGlanceConfigService.getConfig().isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        for (fileEditor in this) {
            if (fileEditor is TextEditor && fileEditor.editor is EditorImpl) {
                withTextEditor(EditorInfo(fileEditor.editor as EditorImpl,where))
            } else if (fileEditor is DiffRequestProcessorEditor) {
                when (val viewer = fileEditor.processor.activeViewer) {
                    is OnesideTextDiffViewer -> if(viewer.editor is EditorImpl) withTextEditor(EditorInfo(viewer.editor as EditorImpl,where))
                    is TwosideTextDiffViewer -> viewer.editors.filterIsInstance<EditorImpl>()
                        .forEachIndexed { index, editor ->
                            withTextEditor(EditorInfo(editor, if (index == 0) BorderLayout.LINE_START else BorderLayout.LINE_END))
                        }
                    is ThreesideTextDiffViewer -> viewer.editors.filterIsInstance<EditorImpl>()
                        .forEachIndexed{ index, editor -> if(index != 1)
                            withTextEditor(EditorInfo(editor, if (index == 0) BorderLayout.LINE_START else BorderLayout.LINE_END))
                        }
                }
            }
        }
    }

    private fun getMyPanel(editor: EditorImpl,placeStr: String): JPanel {
        val glancePanel = GlancePanel(project, editor)
        val placeIndex = if (placeStr == BorderLayout.LINE_START) GlancePanel.PlaceIndex.Left else GlancePanel.PlaceIndex.Right
        editor.putUserData(GlancePanel.CURRENT_GLANCE_PLACE_INDEX, placeIndex)
        val jPanel = if (CodeGlanceConfigService.getConfig().hideOriginalScrollBar) MyPanel(glancePanel).apply {
            glancePanel.myVcsPanel = MyVcsPanel(glancePanel)
            add(glancePanel.myVcsPanel!!, if (placeIndex == GlancePanel.PlaceIndex.Left) BorderLayout.EAST else BorderLayout.WEST)
        } else glancePanel
        glancePanel.hideScrollBarListener.addHideScrollBarListener()
        return jPanel
    }

    private fun Component.applyGlancePanel(block: GlancePanel.()->Unit): GlancePanel?{
        val glancePanel = if (this is MyPanel) panel else if (this is GlancePanel) this else null
        glancePanel?.block()
        return glancePanel
    }

    internal class MyPanel(val panel: GlancePanel):JPanel(BorderLayout()){
        init{
            add(panel)
            isOpaque = false
        }
    }

    override fun dispose() {}

    private data class EditorInfo(val editor: EditorImpl, val place: String)
}