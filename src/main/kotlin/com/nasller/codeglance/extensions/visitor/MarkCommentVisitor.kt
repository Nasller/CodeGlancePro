package com.nasller.codeglance.extensions.visitor

import MARK_REGEX
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
			MARK_REGEX?.find(text)?.let {
				val beforeMark = text.take(it.range.first)
				if (!isOnlySpecialPrefix(beforeMark)) return
				val textRange = element.textRange
				val index = text.indexOf('\n',it.range.last)
				val start = it.range.last + textRange.startOffset + 1
				val blockCommentSuffix by lazy(LazyThreadSafetyMode.NONE) { getLanguageBlockCommentSuffix(element.language) ?: "" }
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

	private fun isOnlySpecialPrefix(prefix: String): Boolean {
		val cleaned = prefix.trim()
		// 匹配：纯注释开头的符号组合，例如 //、/*、#、-- 等
		return cleaned.isEmpty() || Util.regex.matches(cleaned)
	}

	private fun getLanguageBlockCommentSuffix(language: Language) : String?{
		return when(language.displayName){
			"C#" -> "*/"
			else -> LanguageCommenters.INSTANCE.forLanguage(language)?.blockCommentSuffix
		}
	}

	override fun suitableForFile(language: Language) = true

	override fun clone(): HighlightVisitor = MarkCommentVisitor()
}