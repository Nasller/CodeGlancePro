package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.cidr.lang.OCLanguage
import com.jetbrains.cidr.lang.parser.OCLexerTokenTypes
import com.nasller.codeglance.util.Util

class MarkClionVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == OCLexerTokenTypes.PRAGMA_DIRECTIVE){
			val lastChild = element.parent.lastChild
			if(lastChild.elementType == OCLexerTokenTypes.IDENTIFIER){
				visitText(lastChild.text, lastChild.textRange, Util.MARK_CLION_REGION_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(language: Language) = language is OCLanguage

	override fun clone(): HighlightVisitor = MarkClionVisitor()
}