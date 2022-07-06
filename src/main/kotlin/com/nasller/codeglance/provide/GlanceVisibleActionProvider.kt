package com.nasller.codeglance.provide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.util.ui.JBUI
import com.nasller.codeglance.panel.AbstractGlancePanel.Companion.CURRENT_GLANCE
import com.nasller.codeglance.util.CodeGlanceIcons
import com.nasller.codeglance.util.message
import javax.swing.JComponent

private class GlanceVisibleActionProvider : InspectionWidgetActionProvider {
	override fun createAction(editor: Editor): AnAction {
		return object : DefaultActionGroup(ToggleVisibleAction(editor)) {
			override fun update(e: AnActionEvent) {
				e.presentation.isEnabledAndVisible = editor.getUserData(CURRENT_GLANCE)?.run {
					config.singleFileVisibleButton
				} ?: false
			}
		}
	}

	private class ToggleVisibleAction(private val editor: Editor) : DumbAwareToggleAction(), CustomComponentAction {
		override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
			object : ActionButton(this, presentation, place, JBUI.emptySize()) {
				override fun getPopState(): Int = if (myRollover && isEnabled) POPPED else NORMAL
			}.also {
				it.setLook(ActionButtonLook.INPLACE_LOOK)
				it.border = JBUI.Borders.empty(1, 2)
			}

		override fun isSelected(e: AnActionEvent): Boolean {
			return editor.getUserData(CURRENT_GLANCE)?.isVisible?:true
		}

		override fun setSelected(e: AnActionEvent, state: Boolean) {
			editor.getUserData(CURRENT_GLANCE)?.apply{
				isVisible = state
				refresh(true, directUpdate = true)
				changeOriginScrollBarWidth()
			}
		}

		override fun update(e: AnActionEvent) {
			super.update(e)
			val presentation = e.presentation
			if(!isSelected(e)){
				presentation.text = message("glance.visible.show")
				presentation.icon = CodeGlanceIcons.GlanceShow
			}else {
				presentation.text = message("glance.visible.hide")
				presentation.icon = CodeGlanceIcons.GlanceHide
			}
		}
	}
}