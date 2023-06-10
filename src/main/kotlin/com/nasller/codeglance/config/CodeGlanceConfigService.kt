package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "CodeGlance Pro", storages = [Storage("CodeGlancePro.xml")])
class CodeGlanceConfigService : SimplePersistentStateComponent<CodeGlanceConfig>(CodeGlanceConfig()) {
	companion object {
		@JvmStatic
		private val ConfigInstance = ApplicationManager.getApplication().getService(CodeGlanceConfigService::class.java)
		@JvmStatic
		fun getConfig(): CodeGlanceConfig = ConfigInstance.state
	}
}