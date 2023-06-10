package com.nasller.codeglance.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareToggleAction
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.config.SettingsChangePublisher

class DisableByDefaultAction : DumbAwareToggleAction() {
	override fun isSelected(e: AnActionEvent): Boolean = CodeGlanceConfigService.getConfig().disabled

	override fun setSelected(e: AnActionEvent, state: Boolean) {
		CodeGlanceConfigService.getConfig().disabled = state
		invokeLater{ SettingsChangePublisher.onGlobalChanged() }
	}

	override fun getActionUpdateThread(): ActionUpdateThread {
		return ActionUpdateThread.BGT
	}
}