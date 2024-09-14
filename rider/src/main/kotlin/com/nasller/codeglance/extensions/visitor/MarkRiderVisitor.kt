package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.rider.languages.fileTypes.csharp.CSharpFileType
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.lexer.CSharpTokenType
import com.nasller.codeglance.util.Util

class MarkRiderVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == CSharpTokenType.PP_START_REGION){
			val psiMessage = PsiTreeUtil.skipWhitespacesForward(element) ?: return
			if(psiMessage.elementType == CSharpTokenType.PP_MESSAGE){
				visitText(psiMessage.text, psiMessage.textRange, Util.MARK_COMMENT_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(fileType: FileType) = fileType is CSharpFileType

	override fun clone(): HighlightVisitor = MarkRiderVisitor()
}