package com.nasller.codeglance.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance

class ShowHideGlanceAction : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        ConfigInstance.state.disabled = !ConfigInstance.state.disabled
    }
}