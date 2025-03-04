package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.util.elementType

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiClass && element.elementType == JavaStubElementTypes.CLASS) {
			visitPsiNameIdentifier(element)
		}
	}

	override fun suitableForFile(language: Language) = language is JavaLanguage

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}