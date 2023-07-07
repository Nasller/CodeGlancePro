package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.editor.EditorKind
import com.intellij.util.messages.Topic
import com.nasller.codeglance.config.enums.ClickTypeEnum
import com.nasller.codeglance.config.enums.MouseJumpEnum

class CodeGlanceConfig : BaseState() {
	var pixelsPerLine by property(4)
	var maxLinesCount by property(100000)
	var moreThanLineDelay by property(3000)
	var disabled by property(false)
	var singleFileVisibleButton by property(true)
	var hideOriginalScrollBar by property(false)
	var autoCalWidthInSplitterMode by property(true)
	var showEditorToolTip by property(true)
	var mouseWheelMoveEditorToolTip by property(false)
	var isRightAligned by property(true)
	var hoveringToShowScrollBar by property(false)
	var delayHoveringToShowScrollBar by property(0)
	var clickType by enum(ClickTypeEnum.CODE_POSITION)
	var jumpOnMouseDown by enum(MouseJumpEnum.MOUSE_DOWN)
	var viewportColor by nonNullString("A0A0A0")
	var viewportBorderColor by nonNullString("00FF00")
	var viewportBorderThickness by property(0)
	var disableLanguageSuffix by nonNullString("ipynb")
	var clean by property(true)
	var locked by property(false)

	var showFilterMarkupHighlight by property(true)
	var showMarkupHighlight by property(true)
	var showVcsHighlight by property(true)
	var showErrorStripesFullLineHighlight by property(true)
	var showOtherFullLineHighlight by property(false)
	var syntaxHighlight by property(true)

	var enableMarker by property(true)
	var markRegex by nonNullString("\\b(MARK: - )\\b*")
	var markersScaleFactor by property(3.0f)

	var diffTwoSide by property(true)
	var diffThreeSide by property(true)
	var diffThreeSideMiddle by property(false)
	var editorKinds by listOf(mutableListOf(EditorKind.MAIN_EDITOR, EditorKind.CONSOLE, EditorKind.PREVIEW, EditorKind.DIFF))
	var mainWidth by property(110)
	var diffWidth by property(50)
	var unTypedWidth by property(50)
	var consoleWidth by property(50)
	var previewWidth by property(50)

	fun singleFileVisibleButton() = !hoveringToShowScrollBar && singleFileVisibleButton

	private fun nonNullString(initialValue: String = "") = property(initialValue) { it == initialValue }

	private fun <T : Any> listOf(value: MutableList<T>) = property(value) { it == value }

	companion object{
		fun EditorKind.getWidth() = when(this){
			EditorKind.UNTYPED -> CodeGlanceConfigService.getConfig().unTypedWidth
			EditorKind.CONSOLE -> CodeGlanceConfigService.getConfig().consoleWidth
			EditorKind.PREVIEW -> CodeGlanceConfigService.getConfig().previewWidth
			EditorKind.DIFF -> CodeGlanceConfigService.getConfig().diffWidth
			else -> CodeGlanceConfigService.getConfig().mainWidth
		}

		fun EditorKind.setWidth(value: Int) {
			when (this) {
				EditorKind.UNTYPED -> CodeGlanceConfigService.getConfig().unTypedWidth = value
				EditorKind.CONSOLE -> CodeGlanceConfigService.getConfig().consoleWidth = value
				EditorKind.PREVIEW -> CodeGlanceConfigService.getConfig().previewWidth = value
				EditorKind.DIFF -> CodeGlanceConfigService.getConfig().diffWidth = value
				else -> CodeGlanceConfigService.getConfig().mainWidth = value
			}
		}
	}
}

val SettingsChangePublisher: SettingsChangeListener = ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.TOPIC)

interface SettingsChangeListener {
	fun onHoveringOriginalScrollBarChanged(value: Boolean) {}

	fun refresh(refreshImage: Boolean) {}

	fun onGlobalChanged() {}

	companion object {
		val TOPIC = Topic.create("CodeGlanceSettingsChanged", SettingsChangeListener::class.java)
	}
}