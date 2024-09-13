package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import com.nasller.codeglance.util.Util
import org.jetbrains.kotlin.psi.KtClass

class MarkKotlinVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is KtClass) {
			val psiIdentifier = element.nameIdentifier ?: return
			if(psiIdentifier.text.isNotBlank()){
				val textRange = psiIdentifier.textRange
				addInfo(getInfo(textRange.startOffset, textRange.endOffset, Util.MARK_CLASS_ATTRIBUTES))
			}
		}
	}

	override fun suitableForFile(extension: String) = extension == "kt"

	override fun clone(): HighlightVisitor = MarkKotlinVisitor()
}