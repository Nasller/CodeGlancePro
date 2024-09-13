package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class MarkScalaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is ScClass) {
			visitJvm(element)
		}
	}

	override fun suitableForFile(extension: String) = extension == "scala"

	override fun clone(): HighlightVisitor = MarkScalaVisitor()
}