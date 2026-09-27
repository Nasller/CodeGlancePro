package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.*
import com.nasller.codeglance.util.METHOD_NAMES
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.matchesConfiguredName

class MarkDartVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		when (element) {
			is DartClass -> visitPsiNameIdentifier(element)
			is DartFunctionDeclarationWithBodyOrNative,
				is DartMethodDeclaration -> {
					val named = element as? PsiNameIdentifierOwner ?: return
					if (METHOD_NAMES.matchesConfiguredName(named.name)) {
						visitPsiNameIdentifier(named, Util.MARK_METHOD_ATTRIBUTES)
					}
				}
				is DartExtensionDeclaration -> {
				val psiElement = PsiTreeUtil.findChildOfType(element, DartId::class.java) ?: return
				visitText(psiElement.text, psiElement.textRange, Util.MARK_CLASS_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(language: Language) = language is DartLanguage

	override fun clone(): HighlightVisitor = MarkDartVisitor()
}