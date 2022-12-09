package com.nasller.codeglance.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareToggleAction
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangePublisher

class DisableByDefaultAction : DumbAwareToggleAction() {
	override fun isSelected(e: AnActionEvent): Boolean = ConfigInstance.state.disabled

	override fun setSelected(e: AnActionEvent, state: Boolean) {
		ConfigInstance.state.disabled = state
		invokeLater{ SettingsChangePublisher.onGlobalChanged() }
	}

	override fun getActionUpdateThread(): ActionUpdateThread {
		return ActionUpdateThread.BGT
	}
}