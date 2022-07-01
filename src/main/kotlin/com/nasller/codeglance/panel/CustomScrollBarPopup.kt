package com.nasller.codeglance.panel

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.getConfigureHighlightingLevelPopup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.util.message
import java.awt.Component
import java.awt.Point
import javax.swing.event.PopupMenuEvent

class CustomScrollBarPopup(private val glancePanel: GlancePanel) : PopupHandler() {
    var isVisible = false
    private val config = glancePanel.config

    override fun invokePopup(comp: Component?, x: Int, y: Int) {
        if (ApplicationManager.getApplication() == null) return
        val file = PsiDocumentManager.getInstance(glancePanel.project).getPsiFile(glancePanel.editor.document) ?: return
        val actionGroup = DefaultActionGroup(
            DumbAwareToggleOptionAction(object : ToggleOptionAction.Option {
                override fun getName(): String = message("popup.hover.minimap")
                override fun isEnabled(): Boolean = config.isRightAligned
                override fun isAlwaysVisible(): Boolean = true
                override fun isSelected(): Boolean = config.hoveringToShowScrollBar
                override fun setSelected(selected: Boolean) {
                    config.hoveringToShowScrollBar = selected
                }
            }),
            DumbAwareToggleOptionAction(object : ToggleOptionAction.Option {
                override fun getName(): String = message("popup.showFullLineError")
                override fun isEnabled(): Boolean = config.hideOriginalScrollBar
                override fun isAlwaysVisible(): Boolean = true
                override fun isSelected(): Boolean = config.showFullLineError
                override fun setSelected(selected: Boolean) {
                    config.showFullLineError = selected
                }
            }),
            DumbAwareToggleOptionAction(object : ToggleOptionAction.Option {
                override fun getName(): String = message("popup.singleFileVisibleButton")
                override fun isEnabled(): Boolean = !config.hoveringToShowScrollBar
                override fun isAlwaysVisible(): Boolean = true
                override fun isSelected(): Boolean = config.singleFileVisibleButton
                override fun setSelected(selected: Boolean) {
                    config.singleFileVisibleButton = selected
                }
            })
        )
        if (DaemonCodeAnalyzer.getInstance(glancePanel.project).isHighlightingAvailable(file)) {
            actionGroup.addSeparator()
            actionGroup.add(createGotoGroup())
            actionGroup.addSeparator()
            actionGroup.add(object : DumbAwareAction(EditorBundle.messagePointer("customize.highlighting.level.menu.item")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val popup = getConfigureHighlightingLevelPopup(e.dataContext)
                    popup?.show(RelativePoint(comp!!, Point(x, y)))
                }
            })
            if (!UIUtil.uiParents(glancePanel.editor.component, false).filter(EditorWindowHolder::class.java).isEmpty) {
                actionGroup.addSeparator()
                actionGroup.add(object : ToggleAction(message("glance.show.editor.preview.popup")) {
                    override fun isSelected(e: AnActionEvent): Boolean = config.showEditorToolTip
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        config.showEditorToolTip = state
                    }
                })
            }
        }
        val menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.RIGHT_EDITOR_GUTTER_POPUP, actionGroup).component
        menu.addPopupMenuListener(object :PopupMenuListenerAdapter(){
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                isVisible = true
            }
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                isVisible = false
                glancePanel.hideScrollBarListener.hideGlanceRequest()
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
                isVisible = false
                glancePanel.hideScrollBarListener.hideGlanceRequest()
            }
        })
        menu.show(comp,x,y)
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

    private class DumbAwareToggleOptionAction(option:Option):ToggleOptionAction(option),DumbAware
}