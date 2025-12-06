package com.nasller.codeglance.util

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.util.SmartList
import com.nasller.codeglance.config.CodeGlanceConfigService

object Util {
	const val PLUGIN_NAME = "CodeGlance Pro"
	const val MIN_WIDTH = 30
	const val MAX_WIDTH = 250
	val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
	val MARK_CLASS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_CLASS_ATTRIBUTES")
	val MARK_CSHARP_REGION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_CSHARP_REGION_ATTRIBUTES")
	val MARK_CLION_REGION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_CLION_REGION_ATTRIBUTES")

	inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
		return when (val size = size) {
			1 -> SmartList(transform(first()))
			0 -> emptyList()
			else -> mapTo(ArrayList(size), transform)
		}
	}

	fun TextAttributesKey.isMarkAttributes() = this == MARK_COMMENT_ATTRIBUTES || this == MARK_CLASS_ATTRIBUTES
			|| this == MARK_CSHARP_REGION_ATTRIBUTES || this == MARK_CLION_REGION_ATTRIBUTES
}

var MARK_REGEX = CodeGlanceConfigService.Config.markRegex.run {
    if(isNotBlank()) Regex(this) else null
}

var METHOD_ANNOTATION = CodeGlanceConfigService.Config.markMethodAnnotation.run {
    if(isNotBlank()) split("\n").map { it.trim() }.filter { it.isNotBlank() }.toSet() else setOf()
}

var METHOD_ANNOTATION_SUFFIX = CodeGlanceConfigService.Config.markMethodAnnotation.run {
    if(isNotBlank()) split("\n").map {
        val lastIndexOf = it.lastIndexOf(".")
        if(lastIndexOf == -1) it.trim() else it.substring(lastIndexOf + 1).trim()
    }.filter { it.isNotBlank() }.toSet() else setOf()
}