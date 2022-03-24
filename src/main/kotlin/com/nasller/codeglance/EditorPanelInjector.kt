package com.nasller.codeglance

import com.intellij.openapi.Disposable
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
import com.nasller.codeglance.panel.OldGlancePanel
import java.awt.BorderLayout
import java.awt.Component
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
            inject(editor)
        }
    }

    /**
     * Here be dragons. No Seriously. Run!
     *
     * We are digging way down into the editor layout. This lets the codeglance panel be right next to the scroll bar.
     * In an ideal world it would be inside the scroll bar... maybe one day.
     *
     * vsch: added handling when the editor is even deeper, inside firstComponent of a JBSplitter, used by idea-multimarkdown
     * and Markdown Support to show split preview. Missed this plugin while editing markdown. These changes got it back.
     *
     * @param editor A text editor to inject into.
     */
    private fun getPanel(editor: TextEditor): JPanel? {
        try {
            val layoutComponent : Component? = if (editor is TextEditorWithPreview) {
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

    private fun inject(editor: TextEditor) {
        val panel = getPanel(editor) ?: return
        val innerLayout = panel.layout as BorderLayout

        val where = if (config.isRightAligned)
            BorderLayout.LINE_END
        else
            BorderLayout.LINE_START
        if (innerLayout.getLayoutComponent(where) == null) {
            val glancePanel = if(config.oldGlance) {
                OldGlancePanel(project,editor)
            } else GlancePanel(project, editor)
            panel.add(glancePanel, where)
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {}

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}

    override fun onRefreshChanged(disable: Boolean,ignore: TextEditor?) {
        try {
            for (editor in FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()) {
                if(ignore == null || ignore.file?.path != editor.file?.path){
                    val panel = getPanel(editor) ?: continue
                    val innerLayout = panel.layout as BorderLayout
                    val where = if (config.isRightAligned)
                        BorderLayout.LINE_END
                    else
                        BorderLayout.LINE_START
                    val layoutComponent = innerLayout.getLayoutComponent(where)
                    if(disable){
                        if(layoutComponent != null && layoutComponent is AbstractGlancePanel<*>){
                            Disposer.dispose(layoutComponent as Disposable)
                            panel.remove(layoutComponent)
                        }
                    }else {
                        if (layoutComponent != null && layoutComponent is AbstractGlancePanel<*>) {
                            Disposer.dispose(layoutComponent as Disposable)
                            panel.remove(layoutComponent)
                        }
                        val glancePanel = if (config.oldGlance) {
                            OldGlancePanel(project, editor)
                        } else GlancePanel(project, editor)
                        panel.add(glancePanel, where)
                    }
                }
            }
        }catch (e:Exception){
            logger.error(e)
        }
    }
}