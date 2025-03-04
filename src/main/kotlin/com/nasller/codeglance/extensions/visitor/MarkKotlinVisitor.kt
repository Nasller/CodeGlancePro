package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class MarkKotlinVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is KtClass && element.elementType == KtStubElementTypes.CLASS) {
			visitPsiNameIdentifier(element)
		}
	}

	override fun suitableForFile(language: Language) = language is KotlinLanguage

	override fun clone(): HighlightVisitor = MarkKotlinVisitor()
}