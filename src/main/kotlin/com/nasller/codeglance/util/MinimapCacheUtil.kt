package com.nasller.codeglance.util

import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.nasller.codeglance.render.Minimap
import java.util.function.Function

internal class MinimapRef(minimap: Minimap) : java.lang.ref.SoftReference<Minimap?>(minimap) {
	private var strongRef: Minimap?

	init {
		strongRef = minimap
	}

	override fun get(): Minimap? {
		val minimap = strongRef ?: super.get()
		// drop on first request
		strongRef = null
		return minimap
	}
}

internal class MinimapCache(imageProvider: Function<in ScaleContext, MinimapRef>) : ScaleContext.Cache<MinimapRef?>(imageProvider) {
	fun get(ctx: ScaleContext): Minimap {
		val ref = getOrProvide(ctx)
		val image = SoftReference.dereference(ref)
		if (image != null) return image
		clear() // clear to recalculate the image
		return get(ctx) // first recalculated image will be non-null
	}
}