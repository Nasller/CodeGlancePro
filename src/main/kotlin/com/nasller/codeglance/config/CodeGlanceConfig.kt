package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.editor.EditorKind
import com.intellij.util.messages.Topic
import com.nasller.codeglance.config.enums.ClickTypeEnum
import com.nasller.codeglance.config.enums.EditorSizeEnum
import com.nasller.codeglance.config.enums.MouseJumpEnum

class CodeGlanceConfig : BaseState() {
	var pixelsPerLine by property(4)
	var editorSize by enum(EditorSizeEnum.Proportional)
	var maxLinesCount by property(100000)
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
	var moveOnly by property(false)
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
	var markRegex by nonNullString("\\b(MARK: - )\\b|\\b(MARK: )\\b|(?:region \\b)")
	var markersScaleFactor by property(3.0f)

	var diffTwoSide by property(true)
	var diffThreeSide by property(true)
	var diffThreeSideMiddle by property(false)
	var editorKindsStr by nonNullString("${EditorKind.MAIN_EDITOR},${EditorKind.PREVIEW},${EditorKind.DIFF}")
	var mainWidth by property(110)
	var diffWidth by property(50)
	var unTypedWidth by property(50)
	var consoleWidth by property(50)
	var previewWidth by property(50)
	var useFastMinimapForMain by property(true)
	var useEmptyMinimapStr by nonNullString(EditorKind.CONSOLE.name)

	fun singleFileVisibleButton() = !hoveringToShowScrollBar && singleFileVisibleButton

	private fun nonNullString(initialValue: String = "") = property(initialValue) { it == initialValue }

	companion object{
		fun EditorKind.getWidth() = when(this){
			EditorKind.UNTYPED -> CodeGlanceConfigService.Config.unTypedWidth
			EditorKind.CONSOLE -> CodeGlanceConfigService.Config.consoleWidth
			EditorKind.PREVIEW -> CodeGlanceConfigService.Config.previewWidth
			EditorKind.DIFF -> CodeGlanceConfigService.Config.diffWidth
			else -> CodeGlanceConfigService.Config.mainWidth
		}

		fun EditorKind.setWidth(value: Int) {
			when (this) {
				EditorKind.UNTYPED -> CodeGlanceConfigService.Config.unTypedWidth = value
				EditorKind.CONSOLE -> CodeGlanceConfigService.Config.consoleWidth = value
				EditorKind.PREVIEW -> CodeGlanceConfigService.Config.previewWidth = value
				EditorKind.DIFF -> CodeGlanceConfigService.Config.diffWidth = value
				else -> CodeGlanceConfigService.Config.mainWidth = value
			}
		}
	}
}

val SettingsChangePublisher: SettingsChangeListener = ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.TOPIC)

interface SettingsChangeListener {
	fun onHoveringOriginalScrollBarChanged(value: Boolean) {}

	fun refreshDataAndImage() {}

	fun onGlobalChanged() {}

	companion object {
		val TOPIC = Topic.create("CodeGlanceSettingsChanged", SettingsChangeListener::class.java)
	}
}