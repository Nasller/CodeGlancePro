package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass

class MarkKotlinVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is KtClass) {
			visitPsiNameIdentifier(element)
		}
	}

	override fun suitableForFile(fileType: FileType) = fileType is KotlinFileType

	override fun clone(): HighlightVisitor = MarkKotlinVisitor()
}