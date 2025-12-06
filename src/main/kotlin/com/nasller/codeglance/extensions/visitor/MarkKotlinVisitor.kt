package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.nasller.codeglance.util.METHOD_ANNOTATION
import com.nasller.codeglance.util.METHOD_ANNOTATION_SUFFIX
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class MarkKotlinVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
        val elementType = element.elementType
        if (elementType == KtStubElementTypes.CLASS && element is KtClass) {
			visitPsiNameIdentifier(element)
		}else if(METHOD_ANNOTATION_SUFFIX.isNotEmpty() &&
            elementType == KtStubElementTypes.REFERENCE_EXPRESSION && element is KtNameReferenceExpression &&
            METHOD_ANNOTATION_SUFFIX.contains(element.text)) {
            val annotationEntry = element.parentOfType<KtAnnotationEntry>() ?: return
            val clazz = when (val target = element.mainReference.resolve()) {
                is KtPrimaryConstructor -> target.getContainingClassOrObject()
                is KtClassOrObject -> target
                is KtTypeAlias -> {
                    val typeRef = target.getTypeReference()
                    when (val typeResolved = (typeRef?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve()) {
                        is KtPrimaryConstructor -> typeResolved.getContainingClassOrObject()
                        is KtClassOrObject -> typeResolved
                        else -> null
                    }
                }
                else -> null
            }
            if(!METHOD_ANNOTATION.contains(clazz?.fqName?.asString())){
                return
            }
            val ktFun = annotationEntry.parentOfType<KtNamedFunction>() ?: return
            visitPsiNameIdentifier(ktFun)
        }
	}

	override fun suitableForFile(language: Language) = language is KotlinLanguage

	override fun clone(): HighlightVisitor = MarkKotlinVisitor()
}