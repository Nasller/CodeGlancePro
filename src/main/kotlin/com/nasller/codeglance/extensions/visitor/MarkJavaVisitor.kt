package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.util.elementType
import com.nasller.codeglance.util.*

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		when (element) {
			is PsiClass if element.elementType == JavaStubElementTypes.CLASS -> {
				visitPsiNameIdentifier(element)
			}

			is PsiMethod -> {
				val matchesConfiguredName = !element.isConstructor && METHOD_NAMES.matchesConfiguredName(element.name)
				val configuredAnnotations = METHOD_ANNOTATIONS
				val hasConfiguredAnnotation = configuredAnnotations.isNotEmpty() &&
						element.modifierList.annotations.any { configuredAnnotations.containsMethodAnnotation(it.qualifiedName) }
				if (matchesConfiguredName || hasConfiguredAnnotation) {
					visitPsiNameIdentifier(element, Util.MARK_METHOD_ATTRIBUTES)
				}
			}
		}
	}

	override fun suitableForFile(language: Language) = language is JavaLanguage

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}