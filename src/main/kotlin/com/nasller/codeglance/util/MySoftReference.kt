package com.nasller.codeglance.util

import java.lang.ref.SoftReference

class MySoftReference<T>(referent: T,useSoft: Boolean) : SoftReference<T>(if(useSoft) referent else null) {
	private val referent: T? = if(useSoft) null else referent

	override fun get(): T? {
		return referent ?: super.get()
	}
}