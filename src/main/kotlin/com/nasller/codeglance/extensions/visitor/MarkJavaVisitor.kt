package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiClass) {
			visitPsiNameIdentifier(element)
		}
	}

	override fun suitableForFile(extension: String) = extension == "java"

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}