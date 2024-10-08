package com.nasller.codeglance.util

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.util.SmartList

object Util {
	const val PLUGIN_NAME = "CodeGlance Pro"
	const val MIN_WIDTH = 30
	const val MAX_WIDTH = 250
	val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
	val MARK_CLASS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_CLASS_ATTRIBUTES")

	inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
		return when (val size = size) {
			1 -> SmartList(transform(first()))
			0 -> emptyList()
			else -> mapTo(ArrayList(size), transform)
		}
	}

	fun TextAttributesKey.isMarkAttributes() = this == MARK_COMMENT_ATTRIBUTES || this == MARK_CLASS_ATTRIBUTES
}