package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.rider.cpp.fileType.CppLanguage
import com.jetbrains.rider.cpp.fileType.lexer.CppTokenTypes
import com.nasller.codeglance.util.Util

class MarkClionRdVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == CppTokenTypes.PRAGMA_DIRECTIVE){
			val lastChild = element.parent.lastChild
			if(lastChild.elementType == CppTokenTypes.IDENTIFIER){
				visitText(lastChild.text, lastChild.textRange, Util.MARK_CLION_REGION_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(language: Language) = language is CppLanguage

	override fun clone(): HighlightVisitor = MarkClionRdVisitor()
}