package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartExtensionDeclaration
import com.jetbrains.lang.dart.psi.DartId
import com.nasller.codeglance.util.Util

class MarkDartVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		when (element) {
			is DartClass -> visitPsiNameIdentifier(element)
			is DartExtensionDeclaration -> {
				val psiElement = PsiTreeUtil.findChildOfType(element, DartId::class.java) ?: return
				visitText(psiElement.text, psiElement.textRange, Util.MARK_CLASS_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(language: Language) = language is DartLanguage

	override fun clone(): HighlightVisitor = MarkDartVisitor()
}