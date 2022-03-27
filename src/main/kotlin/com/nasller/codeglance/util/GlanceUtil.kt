package com.nasller.codeglance.util

import com.intellij.openapi.editor.markup.TextAttributes

fun attributesImpactForegroundColor(attributes: TextAttributes?): Boolean {
	return attributes === TextAttributes.ERASE_MARKER || attributes != null && attributes.foregroundColor != null
}