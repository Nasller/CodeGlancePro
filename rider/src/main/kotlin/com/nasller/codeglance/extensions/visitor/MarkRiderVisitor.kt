package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.lexer.CSharpTokenType
import com.nasller.codeglance.util.Util

class MarkRiderVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == CSharpTokenType.PP_START_REGION){
			val psiMessage = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace::class.java) ?: return
			if(psiMessage.elementType == CSharpTokenType.PP_MESSAGE && psiMessage.text.isNotBlank()){
				val textRange = psiMessage.textRange
				addInfo(getInfo(textRange.startOffset, textRange.endOffset, Util.MARK_COMMENT_ATTRIBUTES))
			}
		}
	}

	override fun suitableForFile(extension: String) = extension == "cs"

	override fun clone(): HighlightVisitor = MarkRiderVisitor()
}