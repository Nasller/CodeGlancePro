package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

/**
* Classes that will not be loaded are recognized by Plugin Verifier, adaptation made for committing to marketplace
*/
class MarkScalaVisitor : MyRainbowVisitor() {
    private val scalaClass = setOf(Class.forName("org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass"),
        Class.forName("org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject"),
        Class.forName("org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait"))

	override fun visit(element: PsiElement) {
		if(scalaClass.any { it.isInstance(element) }) {
			visitPsiNameIdentifier(element as PsiNameIdentifierOwner)
		}
	}

	override fun suitableForFile(language: Language) = language.id.equals("scala", true)

	override fun clone(): HighlightVisitor = MarkScalaVisitor()
}