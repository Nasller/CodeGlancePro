package com.nasller.codeglance.extensions.visitor

import MARK_REGEX
import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.lexer.CSharpTokenType
import com.nasller.codeglance.util.Util

class RiderRainbowVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiComment) {
			val text = element.text
			MARK_REGEX?.find(text)?.let {
				val textRange = element.textRange
				val index = text.indexOf('\n',it.range.last)
				val start = it.range.last + textRange.startOffset + 1
				val end = if (index > 0) index + textRange.startOffset else {
					textRange.endOffset - if(index < 0 && text.endsWith("*/")) 2 else 0
				}
				if(start != end) {
					addInfo(getInfo(start, end, Util.MARK_COMMENT_ATTRIBUTES))
				}
			}
		}else if(element.elementType == CSharpTokenType.PP_START_REGION){
			val psiMessage = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace::class.java) ?: return
			if(psiMessage.elementType == CSharpTokenType.PP_MESSAGE && psiMessage.text.isNotBlank()){
				val textRange = psiMessage.textRange
				addInfo(getInfo(textRange.startOffset, textRange.endOffset, Util.MARK_COMMENT_ATTRIBUTES))
			}
		}
	}

	override fun suitableForFile(extension: String) = extension == "cs"

	override fun clone(): HighlightVisitor = RiderRainbowVisitor()
}