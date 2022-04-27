package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import kotlin.properties.Delegates

class Config {
    var pixelsPerLine: Int = 4
    var maxLinesCount: Int = 100000
    var disabled: Boolean by Delegates.observable(false) { _, oldValue: Boolean, newValue: Boolean ->
        if (oldValue != newValue) {
            SettingsChangePublisher.onRefreshChanged()
        }
    }
    var hideOriginalScrollBar: Boolean = false
    var hoveringToShowScrollBar: Boolean by Delegates.observable(false) { _, oldValue: Boolean, newValue: Boolean ->
        if (oldValue != newValue) {
            SettingsChangePublisher.onHoveringOriginalScrollBarChanged(newValue)
        }
    }
    var jumpOnMouseDown: Boolean = true
    var width: Int = 110
    var viewportColor: String = "A0A0A0"
    var clean: Boolean = true
    var locked: Boolean = false
}

val SettingsChangePublisher: SettingsChangeListener =
    ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.TOPIC)

interface SettingsChangeListener {

    fun onRefreshChanged() {}

    fun onHoveringOriginalScrollBarChanged(value:Boolean) {}

    fun onGlobalChanged() {}

    companion object {
        val TOPIC: Topic<SettingsChangeListener> =
            Topic.create("CodeGlanceSettingsChanged", SettingsChangeListener::class.java)
    }
}