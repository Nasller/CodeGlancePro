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
import com.nasller.codeglance.config.Config
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
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
    private val config: Config = ConfigInstance.state
    init{
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(SettingsChangeListener.TOPIC, this)
    }

    override fun fileOpened(fem: FileEditorManager, virtualFile: VirtualFile) {
        for (editor in fem.allEditors.filterIsInstance<TextEditor>()) {
            val panel = editor.editor.component as? JPanel ?: continue
            if (panel.layout is BorderLayout && editor.editor is EditorImpl
                && (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.LINE_END) == null) {
                val myPanel = getMyPanel(editor)
                editor.editor.component.add(myPanel, BorderLayout.LINE_END)
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
        try {
            for (editor in FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()) {
                val panel = editor.editor.component as? JPanel ?: continue
                if (panel.layout is BorderLayout && editor.editor is EditorImpl) {
                    val myPanel = getMyPanel(editor)
                    (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.LINE_END)?.removeComponent(panel,myPanel)
                    panel.add(myPanel, BorderLayout.LINE_END)
                    when (myPanel) {
                        is MyPanel -> myPanel.panel.updateImageSoon()
                        is AbstractGlancePanel -> myPanel.updateImageSoon()
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
        val oldGlancePanel = if (this is MyPanel) this.panel else if(this is AbstractGlancePanel) this else null
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