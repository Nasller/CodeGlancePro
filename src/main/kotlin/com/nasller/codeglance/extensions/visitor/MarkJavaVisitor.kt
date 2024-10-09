package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.util.elementType

class MarkJavaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiClass && element.elementType == JavaStubElementTypes.CLASS) {
			visitPsiNameIdentifier(element)
		}
	}

	override fun suitableForFile(fileType: FileType) = fileType is JavaFileType

	override fun clone(): HighlightVisitor = MarkJavaVisitor()
}