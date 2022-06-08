// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.nasller.codeglance

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

const val BUNDLE: @NonNls String = "messages.CodeGlanceBundle"

object CodeGlanceBundle : DynamicBundle(BUNDLE)

@Nls
fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String {
	return CodeGlanceBundle.getMessage(key, *params)
}

fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<String> {
	return CodeGlanceBundle.getLazyMessage(key, *params)
}