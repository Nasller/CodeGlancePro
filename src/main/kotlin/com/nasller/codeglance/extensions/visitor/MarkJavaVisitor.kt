package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.util.elementType
import com.nasller.codeglance.util.METHOD_ANNOTATION

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiClass && element.elementType == JavaStubElementTypes.CLASS) {
			visitPsiNameIdentifier(element)
		}else if(METHOD_ANNOTATION.isNotEmpty() &&
            element is PsiAnnotation &&
            element.elementType == JavaStubElementTypes.ANNOTATION &&
            METHOD_ANNOTATION.contains(element.qualifiedName)) {
            val owner = (element.owner as? PsiModifierList)?.parent as? PsiMethod ?: return
            visitPsiNameIdentifier(owner)
        }
	}

	override fun suitableForFile(language: Language) = language is JavaLanguage

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}