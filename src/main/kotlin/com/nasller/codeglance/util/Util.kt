package com.nasller.codeglance.util

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.util.SmartList

object Util {
	val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
	const val PLUGIN_NAME = "CodeGlance Pro"

	inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
		return when (val size = size) {
			1 -> SmartList(transform(first()))
			0 -> emptyList()
			else -> mapTo(ArrayList(size), transform)
		}
	}
}