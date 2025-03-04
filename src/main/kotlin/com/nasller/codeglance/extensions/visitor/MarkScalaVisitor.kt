package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

class MarkScalaVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if(element is ScClass || element is ScTrait || element is ScObject) {
			visitPsiNameIdentifier(element as PsiNameIdentifierOwner)
		}
	}

	override fun suitableForFile(language: Language) = language is ScalaLanguage

	override fun clone(): HighlightVisitor = MarkScalaVisitor()
}