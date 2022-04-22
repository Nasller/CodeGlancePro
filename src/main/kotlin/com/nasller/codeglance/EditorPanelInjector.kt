package com.nasller.codeglance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.*
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
import javax.swing.JLayeredPane
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
        // Seems there is a case where multiple split panes can have the same file open and getSelectedEditor, and even
        // getEditors(virtualFile) return only one of them... So shotgun approach here.
        for (editor in fem.allEditors.filterIsInstance<TextEditor>()) {
            val panel = getPanel(editor) ?: continue
            if ((panel.layout as BorderLayout).getLayoutComponent(BorderLayout.LINE_END) == null) {
                val myPanel = getMyPanel(editor, panel)
                panel.add(myPanel, BorderLayout.LINE_END)
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
                val panel = getPanel(editor) ?: continue
                val myPanel = getMyPanel(editor,panel)
                (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.LINE_END)?.removeComponent(panel,myPanel)
                panel.add(myPanel, BorderLayout.LINE_END)
                when (myPanel) {
                    is MyPanel -> myPanel.panel.updateImageSoon()
                    is AbstractGlancePanel -> myPanel.updateImageSoon()
                }
            }
        }catch (e:Exception){
            logger.error(e)
        }
    }

    /**
     * Here be dragons. No Seriously. Run!
     *
     * We are digging way down into the editor layout. This lets the codeGlance panel be right next to the scroll bar.
     * In an ideal world it would be inside the scroll bar... maybe one day.
     *
     * added handling when the editor is even deeper, inside firstComponent of a JBSplitter, used by idea-multi-markdown
     * and Markdown Support to show split preview. Missed this plugin while editing markdown. These changes got it back.
     *
     * @param editor A text editor to inject into.
     */
    private fun getPanel(editor: TextEditor): JPanel? {
        try {
            val layoutComponent = if (editor is TextEditorWithPreview) {
                (editor.textEditor.component.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
            }else if(editor.component.layout != null){
                (editor.component.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
            }else null
            val pane:JLayeredPane? = layoutComponent as? JLayeredPane
            return when {
                pane == null -> null
                pane.componentCount > 1 -> pane.getComponent(1)
                else -> pane.getComponent(0)
            } as? JPanel
        } catch (e: ClassCastException) {
            logger.warn("Injection failed")
            e.printStackTrace()
            return null
        }
    }

    private fun getMyPanel(editor: TextEditor,panel: JPanel): JPanel {
        val glancePanel = GlancePanel(project, editor, panel)
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