package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.nasller.codeglance.util.*
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes


class MarkKotlinVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		when (element) {
			is KtClass if PsiUtilCore.getElementType(element) == KtStubElementTypes.CLASS -> {
				visitPsiNameIdentifier(element)
			}

			is KtNamedFunction -> {
				val matchesConfiguredName = !element.isLocal() && !element.isAnonymous && METHOD_NAMES.matchesConfiguredName(element.name)
				if (matchesConfiguredName || element.hasConfiguredMethodAnnotation()) {
					visitPsiNameIdentifier(element, Util.MARK_METHOD_ATTRIBUTES)
				}
			}
		}
	}

	private fun KtNamedFunction.hasConfiguredMethodAnnotation(): Boolean {
		val configuredAnnotations = METHOD_ANNOTATIONS
		return configuredAnnotations.isNotEmpty() && annotationEntries.any { configuredAnnotations.containsMethodAnnotation(it.resolveAnnotationFqName()) }
	}

	private fun KtAnnotationEntry.resolveAnnotationFqName(): String? {
		val target = (typeReference?.typeElement as? KtUserType)
			?.referenceExpression
			?.mainReference
			?.resolve()
		return target.resolveAnnotationFqName()
	}

	private fun PsiElement?.resolveAnnotationFqName(visited: MutableSet<PsiElement> = mutableSetOf()): String? {
		val target = this ?: return null
		if (!visited.add(target)) return null
		return when (target) {
			is KtConstructor<*> -> target.getContainingClassOrObject().fqName?.asString()
			is KtClassOrObject -> target.fqName?.asString()
			is KtTypeAlias -> {
				val expandedTarget = (target.getTypeReference()?.typeElement as? KtUserType)
					?.referenceExpression
					?.mainReference
					?.resolve()
				expandedTarget.resolveAnnotationFqName(visited)
			}
			is PsiClass -> target.qualifiedName
			else -> null
		}
	}

	override fun suitableForFile(language: Language) = language is KotlinLanguage

	override fun clone(): HighlightVisitor = MarkKotlinVisitor()
}