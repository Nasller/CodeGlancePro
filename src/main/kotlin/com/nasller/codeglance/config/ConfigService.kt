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
class ConfigService : PersistentStateComponent<Config> {
    private val config = Config()

    override fun getState(): Config = config

    override fun loadState(config: Config) {
        XmlSerializerUtil.copyBean(config, this.config)
    }

    companion object{
        val ConfigInstance: ConfigService = ApplicationManager.getApplication().getService(ConfigService::class.java)
    }
}