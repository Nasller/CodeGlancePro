package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.util.messages.Topic
import kotlin.properties.Delegates

class Config {
    var pixelsPerLine: Int = 4
    var minLineCount: Int = 1
    var disabled: Boolean by Delegates.observable(false) { _, oldValue: Boolean, newValue: Boolean ->
        if (oldValue != newValue) {
            SettingsChangePublisher.onRefreshChanged(newValue,null)
        }
    }
    var jumpOnMouseDown: Boolean = true
    var width: Int = 110
    var viewportColor: String by Delegates.observable("A0A0A0") { _, oldValue: String, newValue: String ->
        if (oldValue != newValue) {
            SettingsChangePublisher.onColorChanged(newValue)
        }
    }
    var clean: Boolean = false
    var isRightAligned: Boolean = true
    var minWindowWidth: Int = 0
    var locked: Boolean = false
    var oldGlance: Boolean by Delegates.observable(false) { _, oldValue: Boolean, newValue: Boolean ->
        if (oldValue != newValue) {
            SettingsChangePublisher.onRefreshChanged(disabled,null)
        }
    }
}

val SettingsChangePublisher: SettingsChangeListener =
    ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.TOPIC)

interface SettingsChangeListener {

    fun onRefreshChanged(disable:Boolean,ignore: TextEditor?) {}

    fun onColorChanged(newValue:String) {}

    companion object {
        val TOPIC: Topic<SettingsChangeListener> =
            Topic.create("CodeGlanceSettingsChanged", SettingsChangeListener::class.java)
    }
}