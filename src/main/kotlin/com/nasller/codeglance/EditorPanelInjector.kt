package com.nasller.codeglance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.AbstractGlancePanel
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.MyVcsPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Injects a panel into any newly created editors.
 */
class EditorPanelInjector(private val project: Project) : FileEditorManagerListener,SettingsChangeListener{
    private val logger = Logger.getInstance(javaClass)
    private val config = ConfigInstance.state
    init{
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(SettingsChangeListener.TOPIC, this)
    }

    override fun fileOpened(fem: FileEditorManager, virtualFile: VirtualFile) {
        val where = if (config.isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        for (editor in fem.allEditors.filterIsInstance<TextEditor>()) {
            val panel = editor.editor.component as? JPanel ?: continue
            val layout = panel.layout
            if (layout is BorderLayout && editor.editor is EditorImpl && layout.getLayoutComponent(where) == null) {
                val myPanel = getMyPanel(editor)
                panel.add(myPanel, where)
                when (myPanel) {
                    is MyPanel -> myPanel.panel.changeOriginScrollBarWidth()
                    is AbstractGlancePanel -> myPanel.changeOriginScrollBarWidth()
                }
            }
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {}

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}

    override fun onGlobalChanged() {
        val where = if (config.isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        try {
            for (editor in FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()) {
                val panel = editor.editor.component as? JPanel ?: continue
                val layout = panel.layout
                if (layout is BorderLayout && editor.editor is EditorImpl) {
                    val myPanel = getMyPanel(editor)
                    layout.getLayoutComponent(BorderLayout.LINE_START)?.removeComponent(panel,myPanel)
                    layout.getLayoutComponent(BorderLayout.LINE_END)?.removeComponent(panel,myPanel)
                    panel.add(myPanel, where)
                    when (myPanel) {
                        is MyPanel -> myPanel.panel.updateImage()
                        is AbstractGlancePanel -> myPanel.updateImage()
                    }
                }
            }
        }catch (e:Exception){
            logger.error(e)
        }
    }

    private fun getMyPanel(editor: TextEditor): JPanel {
        val glancePanel = GlancePanel(project, editor)
        val jPanel = if (config.hideOriginalScrollBar) MyPanel(glancePanel).apply {
            glancePanel.myVcsPanel = MyVcsPanel(glancePanel)
            add(glancePanel.myVcsPanel!!, BorderLayout.WEST)
        } else glancePanel
        glancePanel.addHideScrollBarListener()
        return jPanel
    }

    private fun Component.removeComponent(parent: JComponent,newComponent: JComponent){
        val oldGlancePanel = if (this is MyPanel) panel else if(this is AbstractGlancePanel) this else null
        oldGlancePanel?.let {
            parent.remove(this)
            Disposer.dispose(it)
            it.changeOriginScrollBarWidth()
            when (newComponent) {
                is MyPanel -> newComponent.panel.originalScrollbarWidth = it.originalScrollbarWidth
                is AbstractGlancePanel -> newComponent.originalScrollbarWidth = it.originalScrollbarWidth
            }
        }
    }

    private class MyPanel(val panel: AbstractGlancePanel):JPanel(){
        init{
            layout = BorderLayout()
            isOpaque = false
            add(panel)
        }
    }
}