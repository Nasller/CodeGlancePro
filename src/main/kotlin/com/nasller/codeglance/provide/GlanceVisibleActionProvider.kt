package com.nasller.codeglance.provide

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAwareToggleAction
import com.nasller.codeglance.panel.AbstractGlancePanel.Companion.CURRENT_GLANCE
import com.nasller.codeglance.util.CodeGlanceIcons
import com.nasller.codeglance.util.message
import javax.swing.JComponent

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

	private class ToggleVisibleAction(private val editor: Editor) : DumbAwareToggleAction(), CustomComponentAction {
		override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
			object : ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
				override fun getPopState(): Int = if (myRollover && isEnabled) POPPED else NORMAL
			}

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
			val presentation = e.presentation
			if(isSelected(e)){
				presentation.text = message("glance.visible.show")
				presentation.icon = CodeGlanceIcons.GlanceShow
			}else {
				presentation.text = message("glance.visible.hide")
				presentation.icon = CodeGlanceIcons.GlanceHide
			}
		}
	}
}