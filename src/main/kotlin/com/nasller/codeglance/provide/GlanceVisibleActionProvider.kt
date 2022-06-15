package com.nasller.codeglance.provide

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAwareToggleAction
import com.nasller.codeglance.message
import com.nasller.codeglance.panel.AbstractGlancePanel.Companion.CURRENT_GLANCE

private class GlanceVisibleActionProvider : InspectionWidgetActionProvider {
	override fun createAction(editor: Editor): AnAction {
		return object : DefaultActionGroup(ToggleVisibleAction(editor)) {
			override fun update(e: AnActionEvent) {
				e.presentation.isEnabledAndVisible = editor.getUserData(CURRENT_GLANCE)?.run {
					config.singleFileVisibleButton && !config.disabled && !config.hoveringToShowScrollBar
				} ?: false
			}
		}
	}

	private class ToggleVisibleAction(private val editor: Editor) : DumbAwareToggleAction(null,
		null, AllIcons.General.InspectionsEye) {

		override fun isSelected(e: AnActionEvent): Boolean {
			return !(editor.getUserData(CURRENT_GLANCE)?.isVisible?:true)
		}

		override fun setSelected(e: AnActionEvent, state: Boolean) {
			editor.getUserData(CURRENT_GLANCE)?.apply{
				isVisible = !state
				myVcsPanel?.let { it.isVisible = isVisible }
				changeOriginScrollBarWidth()
				updateImage()
			}
		}

		override fun update(e: AnActionEvent) {
			super.update(e)
			e.presentation.text = message(if(isSelected(e)) "glance.visible.show" else "glance.visible.hide")
		}
	}
}