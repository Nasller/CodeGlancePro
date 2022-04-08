package com.nasller.codeglance.panel

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.getConfigureHighlightingLevelPopup
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Point

class CustomDaemonEditorPopup(private val myProject: Project,private val myEditor: Editor) : PopupHandler() {

    override fun invokePopup(comp: Component?, x: Int, y: Int) {
        if (ApplicationManager.getApplication() == null) return
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.document) ?: return
        val actionGroup = DefaultActionGroup()
        actionGroup.add(createGotoGroup())
        actionGroup.addSeparator()
        actionGroup.add(object :
            DumbAwareAction(EditorBundle.messagePointer("customize.highlighting.level.menu.item")) {
            override fun actionPerformed(e: AnActionEvent) {
                val popup = getConfigureHighlightingLevelPopup(e.dataContext)
                popup?.show(RelativePoint(comp!!, Point(x, y)))
            }
        })
        if (!UIUtil.uiParents(myEditor.component, false).filter(EditorWindowHolder::class.java).isEmpty) {
            actionGroup.addSeparator()
            actionGroup.add(object : ToggleAction(IdeBundle.message("checkbox.show.editor.preview.popup")) {
                override fun isSelected(e: AnActionEvent): Boolean {
                    return UISettings.instance.showEditorToolTip
                }

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    UISettings.instance.showEditorToolTip = state
                    UISettings.instance.fireUISettingsChanged()
                }
            })
        }
        if (DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(file)) {
            ActionManager.getInstance().createActionPopupMenu(ActionPlaces.RIGHT_EDITOR_GUTTER_POPUP, actionGroup).component.show(comp, x, y)
        }
    }

    private companion object{
        fun createGotoGroup(): DefaultActionGroup {
            val shortcut = KeymapUtil.getPrimaryShortcut("GotoNextError")
            val shortcutText = if (shortcut != null) " (" + KeymapUtil.getShortcutText(shortcut) + ")" else ""
            val gotoGroup = DefaultActionGroup.createPopupGroup {
                CodeInsightBundle.message("popup.title.next.error.action.0.goes.through", shortcutText)
            }
            gotoGroup.add(object : ToggleAction(EditorBundle.message("errors.panel.go.to.errors.first.radio")) {
                override fun isSelected(e: AnActionEvent): Boolean =
                    DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst = state
                }

                override fun isDumbAware(): Boolean = true
            })
            gotoGroup.add(object : ToggleAction(EditorBundle.message("errors.panel.go.to.next.error.warning.radio")) {
                override fun isSelected(e: AnActionEvent): Boolean =
                    !DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst = !state
                }

                override fun isDumbAware(): Boolean = true
            })
            return gotoGroup
        }
    }
}