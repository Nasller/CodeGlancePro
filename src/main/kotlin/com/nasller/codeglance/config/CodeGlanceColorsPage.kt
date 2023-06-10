package com.nasller.codeglance.config

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.nasller.codeglance.util.message
import javax.swing.Icon

class CodeGlanceColorsPage : ColorSettingsPage, DisplayPrioritySortable {
	override fun getAttributeDescriptors(): Array<AttributesDescriptor> = CODE_GLANCE_PRO_ATTRIBUTES

	override fun getColorDescriptors(): Array<ColorDescriptor> = emptyArray()

	override fun getDisplayName(): String = CODE_GLANCE_PRO_GROUP

	override fun getIcon(): Icon = FileTypes.PLAIN_TEXT.icon

	override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

	override fun getDemoText(): String = "//${CodeGlanceConfigService.getConfig().markRegex} <mark_comment>This is a comment</mark_comment>"

	override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> = ADDITIONAL_HIGHLIGHT_DESCRIPTORS

	override fun getPriority(): DisplayPriority = DisplayPriority.OTHER_SETTINGS

	companion object {
		private const val CODE_GLANCE_PRO_GROUP = "CodeGlance Pro"
		@JvmStatic
		val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
		@JvmStatic
		private val ADDITIONAL_HIGHLIGHT_DESCRIPTORS = mutableMapOf(Pair("mark_comment",MARK_COMMENT_ATTRIBUTES))

		@JvmStatic
		private val CODE_GLANCE_PRO_ATTRIBUTES = arrayOf(
			AttributesDescriptor(message("settings.color.descriptor.mark.comment"), MARK_COMMENT_ATTRIBUTES)
		)
	}
}