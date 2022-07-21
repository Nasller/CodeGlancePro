package com.nasller.codeglance.util

import com.intellij.ui.IconManager
import javax.swing.Icon

object CodeGlanceIcons {
	@JvmField
	val GlanceShow = load("/icons/glanceShow.svg")

	@JvmField
	val GlanceHide = load("/icons/glanceHide.svg")

	@JvmField
	val Widget = load("/icons/widget.svg")

	@JvmStatic
	fun load(path: String): Icon {
		return IconManager.getInstance().getIcon(path, CodeGlanceIcons::class.java)
	}
}