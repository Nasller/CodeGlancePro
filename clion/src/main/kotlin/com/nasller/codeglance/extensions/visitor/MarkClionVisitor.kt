package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.cidr.lang.OCLanguage
import com.jetbrains.cidr.lang.parser.OCLexerTokenTypes
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration
import com.jetbrains.cidr.lang.psi.OCMethod
import com.nasller.codeglance.util.METHOD_NAMES
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.matchesConfiguredName

class MarkClionVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == OCLexerTokenTypes.PRAGMA_DIRECTIVE){
			val lastChild = element.parent.lastChild
			if(lastChild.elementType == OCLexerTokenTypes.IDENTIFIER){
				visitText(lastChild.text, lastChild.textRange, Util.MARK_CLION_REGION_ATTRIBUTES)
			}
		}
		when (element) {
			is OCFunctionDeclaration -> {
				val nameIdentifier = element.nameIdentifier ?: return
				if (METHOD_NAMES.matchesConfiguredName(nameIdentifier.text)) {
					visitText(nameIdentifier.text, nameIdentifier.textRange, Util.MARK_METHOD_ATTRIBUTES)
				}
			}
			is OCMethod -> {
						val className = element.containingClass.name
						if (className != null && (element.selector == className || element.selector == "~$className")) return
						if (METHOD_NAMES.matchesConfiguredName(element.selector)) {
							visitPsiNameIdentifier(element, Util.MARK_METHOD_ATTRIBUTES)
						}
			}
		}
	}

	override fun suitableForFile(language: Language) = language is OCLanguage

	override fun clone(): HighlightVisitor = MarkClionVisitor()
}