package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.cidr.lang.OCFileType
import com.jetbrains.cidr.lang.parser.OCLexerTokenTypes
import com.nasller.codeglance.util.Util

class MarkClionVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element.elementType == OCLexerTokenTypes.PRAGMA_DIRECTIVE){
			val psiMessage = PsiTreeUtil.skipWhitespacesForward(element) ?: return
			if(psiMessage.elementType == OCLexerTokenTypes.IDENTIFIER){
				visitText(psiMessage.text, psiMessage.textRange, Util.MARK_REGION_ATTRIBUTES)
			}
		}
	}

	override fun suitableForFile(fileType: FileType) = fileType is OCFileType

	override fun clone(): HighlightVisitor = MarkClionVisitor()
}