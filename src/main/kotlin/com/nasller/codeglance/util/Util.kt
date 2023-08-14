package com.nasller.codeglance.util

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.nasller.codeglance.config.CodeGlanceConfigService

object Util {
	val MARK_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MARK_COMMENT_ATTRIBUTES")
	const val PLUGIN_NAME = "CodeGlance Pro"
	var MARK_REGEX = CodeGlanceConfigService.getConfig().markRegex.run {
		if(isNotBlank()) Regex(this) else null
	}
}