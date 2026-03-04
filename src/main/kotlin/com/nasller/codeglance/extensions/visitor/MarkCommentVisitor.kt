package com.nasller.codeglance.extensions.visitor

import MyRainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.nasller.codeglance.util.MARK_REGEX
import com.nasller.codeglance.util.Util

class MarkCommentVisitor : MyRainbowVisitor() {
	override fun visit(element: PsiElement) {
		if (element is PsiComment) {
			val text = element.text
			MARK_REGEX?.find(text)?.let {
				val language = element.language
				val beforeMark = text.take(it.range.first)
				val commenter = LanguageCommenters.INSTANCE.forLanguage(language)
				if (!isOnlySpecialPrefix(beforeMark, commenter, language)) return
				val textRange = element.textRange
				val index = text.indexOf('\n',it.range.last)
				val start = it.range.last + textRange.startOffset + 1
				val blockCommentSuffix by lazy(LazyThreadSafetyMode.NONE) { getLanguageBlockCommentSuffix(commenter, language) ?: "" }
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

	private fun isOnlySpecialPrefix(prefix: String, commenter: Commenter?, language: Language): Boolean {
		val cleaned = prefix.trim()
		if(cleaned.isEmpty()){
			return false
		}
		return Regex("""^[\s${getLanguageCommentPrefix(commenter, language)}]+$""").matches(cleaned)
	}

	private fun getLanguageCommentPrefix(commenter: Commenter?, language: Language): String {
		fun escape(s: String?) = s?.let { Regex.escape(it) }.orEmpty()
		return when (language.displayName) {
			"C#" -> listOf("//", "/*").joinToString("") { escape(it) }
			else -> buildList {
				commenter?.lineCommentPrefixes
					?.filter { it.isNotEmpty() }
					?.forEach { add(escape(it)) }
				commenter?.blockCommentPrefix?.let { add(escape(it)) }
			}.joinToString("")
		}
	}

	private fun getLanguageBlockCommentSuffix(commenter: Commenter?, language: Language) : String?{
		return when(language.displayName){
			"C#" -> "*/"
			else -> commenter?.blockCommentSuffix
		}
	}

	override fun suitableForFile(language: Language) = true

	override fun clone(): HighlightVisitor = MarkCommentVisitor()
}