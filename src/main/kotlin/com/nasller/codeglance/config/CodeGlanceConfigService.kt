package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "CodeGlance Pro",
    storages = [Storage("CodeGlancePro.xml")]
)
class CodeGlanceConfigService : PersistentStateComponent<CodeGlanceConfig> {
    private val config = CodeGlanceConfig()

    override fun getState() = config

    override fun loadState(config: CodeGlanceConfig) {
        XmlSerializerUtil.copyBean(config, this.config)
    }

    companion object{
        val ConfigInstance: CodeGlanceConfigService = ApplicationManager.getApplication().getService(CodeGlanceConfigService::class.java)
    }
}