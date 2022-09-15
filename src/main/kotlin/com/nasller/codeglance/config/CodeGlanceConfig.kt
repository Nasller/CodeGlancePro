package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.util.messages.Topic
import com.nasller.codeglance.config.enums.MouseJumpEnum
import kotlin.properties.Delegates

class CodeGlanceConfig : BaseState() {
	var pixelsPerLine by property(4)
	var maxLinesCount by property(100000)
	var moreThanLineDelay by property(3000)
	var disabled by property(false)
	var singleFileVisibleButton by property(true)
	var hideOriginalScrollBar by property(false)
	var showFullLineHighlight by property(true)
	var autoCalWidthInSplitterMode by property(true)
	var showEditorToolTip by property(true)
	var isRightAligned by property(true)
	var hoveringToShowScrollBar by Delegates.observable(false) { _, oldValue, newValue ->
		if (oldValue != newValue) {
			SettingsChangePublisher.onHoveringOriginalScrollBarChanged(newValue)
		}
	}
	var jumpOnMouseDown by enum(MouseJumpEnum.MOUSE_DOWN)
	var width by property(110)
	var viewportColor by string("A0A0A0")
	var clean by property(true)
	var locked by property(false)

	fun singleFileVisibleButton() = !hoveringToShowScrollBar && singleFileVisibleButton
}

val SettingsChangePublisher = ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.TOPIC)

interface SettingsChangeListener {
	fun onHoveringOriginalScrollBarChanged(value: Boolean) {}

	fun refresh(directUpdate: Boolean = false, updateScroll: Boolean = false) {}

	fun onGlobalChanged() {}

	companion object {
		val TOPIC = Topic.create("CodeGlanceSettingsChanged", SettingsChangeListener::class.java)
	}
}