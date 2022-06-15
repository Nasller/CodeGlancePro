package com.nasller.codeglance.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance

class ShowHideGlanceAction : DumbAwareAction() {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        ConfigInstance.state.disabled = !ConfigInstance.state.disabled
    }
}