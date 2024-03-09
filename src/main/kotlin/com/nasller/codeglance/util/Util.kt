package com.nasller.codeglance.util

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.util.SmartList
import com.intellij.ui.scale.DerivedScaleType
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.panel.GlancePanel

object Util {
	val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
	const val PLUGIN_NAME = "CodeGlance Pro"
	var MARK_REGEX = CodeGlanceConfigService.getConfig().markRegex.run {
		if(isNotBlank()) Regex(this) else null
	}

	inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
		return when (val size = size) {
			1 -> SmartList(transform(first()))
			0 -> emptyList()
			else -> mapTo(ArrayList(size), transform)
		}
	}

	fun Int.alignedToY(glancePanel: GlancePanel) = (this / glancePanel.scaleContext.getScale(DerivedScaleType.PIX_SCALE)).toInt()
}