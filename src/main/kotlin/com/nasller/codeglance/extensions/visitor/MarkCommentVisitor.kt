package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.nasller.codeglance.util.Util

class MarkCommentVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiComment) {
			val text = element.text
			Util.MARK_REGEX?.find(text)?.let {
				val textRange = element.textRange
				val index = text.indexOf('\n',it.range.last)
				val blockCommentSuffix by lazy(LazyThreadSafetyMode.NONE) { getLanguageBlockCommentSuffix(element.language) ?: "" }
				val end = if (index > 0) index + textRange.startOffset else {
					textRange.endOffset - if(index < 0 && blockCommentSuffix.isNotBlank() && text.endsWith(blockCommentSuffix)){
						blockCommentSuffix.length
					} else 0
				}
				addInfo(getInfo(it.range.last + textRange.startOffset + 1, end, Util.MARK_COMMENT_ATTRIBUTES))
			}
		}
	}

	private fun getLanguageBlockCommentSuffix(language: Language) : String?{
		return when(language.displayName){
			"C#" -> "*/"
			else -> LanguageCommenters.INSTANCE.forLanguage(language)?.blockCommentSuffix
		}
	}

	override fun clone(): HighlightVisitor = MarkCommentVisitor()
}