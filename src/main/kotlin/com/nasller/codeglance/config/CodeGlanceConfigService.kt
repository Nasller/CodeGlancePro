package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.nasller.codeglance.util.Util

@State(name = Util.PLUGIN_NAME, storages = [Storage("CodeGlancePro.xml")])
class CodeGlanceConfigService : SimplePersistentStateComponent<CodeGlanceConfig>(CodeGlanceConfig()) {
	companion object {
		private val ConfigInstance by lazy { ApplicationManager.getApplication().getService(CodeGlanceConfigService::class.java) }

		fun getConfig(): CodeGlanceConfig = ConfigInstance.state
	}
}