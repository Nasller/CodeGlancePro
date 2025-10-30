package com.nasller.codeglance.extensions.visitor

import MARK_REGEX
import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.nasller.codeglance.util.Util

class MarkCommentVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiComment) {
			val commenter = LanguageCommenters.INSTANCE.forLanguage(element.language)
			val blockCommentPrefix = getLanguageBlockCommentPrefix(element, commenter)
			val text = if(blockCommentPrefix.isNotBlank()) element.text.substring(blockCommentPrefix.length) else element.text
			MARK_REGEX?.find(text)?.let {
				val textRange = element.textRange
				val index = text.indexOf('\n',it.range.last)
				val blockCommentSuffix by lazy(LazyThreadSafetyMode.NONE) { getLanguageBlockCommentSuffix(element.language, commenter) ?: "" }
				val start = blockCommentPrefix.length + it.range.last + textRange.startOffset + 1
				val end = if (index > 0) index + textRange.startOffset else {
					textRange.endOffset - if(index < 0 && blockCommentSuffix.isNotBlank() && text.endsWith(blockCommentSuffix)){
						blockCommentSuffix.length
					} else 0
				}
				if(start != end) {
					addInfo(getInfo(start, end, Util.MARK_COMMENT_ATTRIBUTES))
				}
			}
		}
	}

	private fun getLanguageBlockCommentPrefix(element: PsiComment, commenter: Commenter?) : String {
		val text = element.text
		commenter?.lineCommentPrefixes?.filter { it != null && text.startsWith(it) }?.first {
			return@getLanguageBlockCommentPrefix it
		}
		commenter?.blockCommentPrefix?.let {
			if(text.startsWith(it)) {
				return@getLanguageBlockCommentPrefix it
			}
		}
		return ""
	}

	private fun getLanguageBlockCommentSuffix(language: Language, commenter: Commenter?) : String?{
		return when(language.displayName){
			"C#" -> "*/"
			else -> commenter?.blockCommentSuffix
		}
	}

	override fun suitableForFile(language: Language) = true

	override fun clone(): HighlightVisitor = MarkCommentVisitor()
}