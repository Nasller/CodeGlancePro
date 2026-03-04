package com.nasller.codeglance.util

import java.lang.ref.SoftReference

interface MySoftReference<T> {
	fun get(): T?

	fun clear()

	fun clear(action: T.() -> Unit) {
		if(this is NoSoftReference) get()?.apply(action)
		clear()
	}

	companion object{
		fun <T> create(referent: T?,useSoft: Boolean): MySoftReference<T> =
			if(useSoft) WithSoftReference(referent) else NoSoftReference(referent)
	}
}

private class NoSoftReference<T>(private var referent: T?): MySoftReference<T>{
	override fun get(): T? = referent

	override fun clear() { referent = null }
}

private class WithSoftReference<T>(referent: T?) : SoftReference<T>(referent), MySoftReference<T>