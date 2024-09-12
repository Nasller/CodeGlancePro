package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.nasller.codeglance.util.Util

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiClass) {
			val psiIdentifier = element.nameIdentifier ?: return
			if(psiIdentifier.text.isNotBlank()){
				val textRange = psiIdentifier.textRange
				addInfo(getInfo(textRange.startOffset, textRange.endOffset, Util.MARK_CLASS_ATTRIBUTES))
			}
		}
	}

	override fun suitableForFile(extension: String) = extension == "java"

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}