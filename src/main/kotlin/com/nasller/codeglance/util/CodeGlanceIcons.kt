package com.nasller.codeglance.util

import com.intellij.ui.IconManager
import com.intellij.ui.RoundedIcon
import com.intellij.util.IconUtil
import com.intellij.util.ImageLoader
import com.intellij.util.ui.JBImageIcon
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

	@JvmStatic
	@Suppress("UnstableApiUsage")
	fun loadRoundImageIcon(path: String): RoundedIcon {
		return RoundedIcon(ImageLoader.loadFromResource(path, CodeGlanceIcons::class.java)!!,50.0)
	}

	@JvmStatic
	fun loadImageIcon(path: String): JBImageIcon {
		return JBImageIcon(ImageLoader.loadFromResource(path, CodeGlanceIcons::class.java)!!)
	}

	@JvmStatic
	fun scaleIcon(icon: Icon, scale: Float): Icon {
		return IconUtil.scale(icon, null, scale)
	}
}