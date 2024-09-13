package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.psi.DartClass

class MarkDartVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is DartClass) {
			visitJvm(element)
		}
	}

	override fun suitableForFile(extension: String) = extension == "dart"

	override fun clone(): HighlightVisitor = MarkDartVisitor()
}