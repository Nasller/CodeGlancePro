package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.rider.languages.fileTypes.csharp.CSharpLanguage
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.lexer.CSharpTokenType
import com.jetbrains.rider.languages.fileTypes.csharp.psi.impl.CSharpMethodDeclaration
import com.nasller.codeglance.util.METHOD_NAMES
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.matchesConfiguredName

class MarkRiderVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == CSharpTokenType.PP_START_REGION){
			val psiMessage = PsiTreeUtil.skipWhitespacesForward(element) ?: return
			if(psiMessage.elementType == CSharpTokenType.PP_MESSAGE){
				visitText(psiMessage.text, psiMessage.textRange, Util.MARK_CSHARP_REGION_ATTRIBUTES)
			}
		}
		if (element is CSharpMethodDeclaration && METHOD_NAMES.matchesConfiguredName(element.name)) {
			visitPsiNameIdentifier(element, Util.MARK_METHOD_ATTRIBUTES)
		}
	}

	override fun suitableForFile(language: Language) = language is CSharpLanguage

	override fun clone(): HighlightVisitor = MarkRiderVisitor()
}