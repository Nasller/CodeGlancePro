package com.nasller.codeglance.config

import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.message
import javax.swing.Icon

class CodeGlanceColorsPage : ColorSettingsPage, DisplayPrioritySortable {
	override fun getAttributeDescriptors() = arrayOf(
		AttributesDescriptor(message("settings.color.descriptor.mark.comment"), Util.MARK_COMMENT_ATTRIBUTES)
	)

	override fun getColorDescriptors(): Array<ColorDescriptor> = emptyArray()

	override fun getDisplayName() = Util.PLUGIN_NAME

	override fun getIcon(): Icon = FileTypes.PLAIN_TEXT.icon

	override fun getHighlighter() = PlainSyntaxHighlighter()

	override fun getDemoText() = "//<mark_comment>This is a comment</mark_comment>"

	override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(Pair("mark_comment",Util.MARK_COMMENT_ATTRIBUTES))

	override fun getPriority() = DisplayPriority.OTHER_SETTINGS
}