package com.nasller.codeglance.visitor

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance

class MarkCommentVisitor : MyRainbowVisitor() {
	override fun suitableForFile(file: PsiFile): Boolean = file.fileType.defaultExtension.isBlank() || ConfigInstance.state.disableLanguageSuffix
		.split(",").toSet().contains(file.fileType.defaultExtension).not()

	override fun visit(element: PsiElement) {
		if (element is PsiComment) {
			regex.find(element.text)?.let {
				val textRange = element.textRange
				val end = element.text.indexOf('\n',it.range.last)
				addInfo(getInfo(it.range.last + textRange.startOffset + 1,
					if(end > 0) end + textRange.startOffset else textRange.endOffset,CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES))
			}
		}
	}

	override fun clone(): HighlightVisitor = MarkCommentVisitor()

	private companion object{
		private val regex = Regex("\\b(MARK: - )\\b*")
	}
}