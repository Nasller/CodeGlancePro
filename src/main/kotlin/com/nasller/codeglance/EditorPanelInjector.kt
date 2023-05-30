package com.nasller.codeglance

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

class EditorPanelInjector(private val project: Project) : FileEditorManagerListener,SettingsChangeListener,LafManagerListener,Disposable {
    private val logger = Logger.getInstance(javaClass)
    private var isFirstSetup = true
    init {
        CodeGlancePlugin.projectMap[project] = this
        ApplicationManager.getApplication().messageBus.connect(this).apply {
            subscribe(LafManagerListener.TOPIC, this@EditorPanelInjector)
            subscribe(SettingsChangeListener.TOPIC, this@EditorPanelInjector)
        }
    }

    /** FileEditorManagerListener */
    override fun fileOpened(fem: FileEditorManager, virtualFile: VirtualFile) {
        val config = ConfigInstance.state
        val extension = virtualFile.fileType.defaultExtension
        if(extension.isNotBlank() && config.disableLanguageSuffix.split(",").toSet().contains(extension)) return
        val where = if (config.isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        for (textEditor in fem.getAllEditors(virtualFile).filterIsInstance<TextEditor>()) {
            val editor = textEditor.editor as? EditorImpl
            val layout = (editor?.component as? JPanel)?.layout
            if (layout is BorderLayout && layout.getLayoutComponent(where) == null) {
                val myPanel = getMyPanel(editor)
                editor.component.add(myPanel, where)
                myPanel.applyGlancePanel { changeOriginScrollBarWidth() }
            }
        }
    }

    /** SettingsChangeListener */
    override fun onGlobalChanged() {
        val where = if (ConfigInstance.state.isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        val disable = ConfigInstance.state.disableLanguageSuffix.split(",").toSet()
        processAllGlanceEditor { component, editor->
            if(component != null) editor.component.remove(component)
            val oldGlancePanel = component?.applyGlancePanel { Disposer.dispose(this) }
            val extension = editor.virtualFile.fileType.defaultExtension
            if(extension.isNotBlank() && disable.contains(extension)) {
                oldGlancePanel?.changeOriginScrollBarWidth(false)
            } else {
                val myPanel = getMyPanel(editor)
                editor.component.add(myPanel, where)
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

    private fun processAllGlanceEditor(action: (component:Component?,editor: EditorImpl)->Unit){
        try {
            for (textEditor in FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()) {
                val editor = textEditor.editor as? EditorImpl
                val layout = (editor?.component as? JPanel)?.layout
                if (layout is BorderLayout) {
                    action((layout.getLayoutComponent(BorderLayout.LINE_END) ?: layout.getLayoutComponent(BorderLayout.LINE_START)),editor)
                }
            }
        }catch (e:Exception){
            logger.error(e)
        }
    }

    private fun getMyPanel(editor: EditorImpl): JPanel {
        val glancePanel = GlancePanel(project, editor)
        val jPanel = if (ConfigInstance.state.hideOriginalScrollBar) MyPanel(glancePanel).apply {
            glancePanel.myVcsPanel = MyVcsPanel(glancePanel)
            add(glancePanel.myVcsPanel!!, BorderLayout.WEST)
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
}