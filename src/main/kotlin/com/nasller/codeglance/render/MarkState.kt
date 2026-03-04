package com.nasller.codeglance.render

import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.DocumentUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.Util.isMarkAttributes
import io.ktor.util.collections.*
import java.awt.Color

class MarkState(val glancePanel: GlancePanel) {
	private val markSet = lazy { ConcurrentSet<RangeHighlighterEx>() }

	fun getAllMarkHighlight(): Collection<RangeHighlighterEx> = if(hasMarkHighlight()) markSet.value else emptyList()

	fun hasMarkHighlight(): Boolean = markSet.isInitialized() && markSet.value.isNotEmpty()

	fun markHighlightChange(group: BookmarkGroup, bookmark: LineBookmark, remove: Boolean): RangeHighlighterEx? {
		if(glancePanel.config.enableBookmarksMark){
			val highlighter = BookmarkHighlightDelegate(group, bookmark)
			if(markHighlightChange(highlighter, remove)){
				return highlighter
			}
		}
		return null
	}

	fun markHighlightChange(highlighter: RangeHighlighterEx, remove: Boolean): Boolean {
		if(highlighter is BookmarkHighlightDelegate||
			highlighter.textAttributesKey?.isMarkAttributes() == true){
			if(highlighter.getErrorStripeMarkColor(glancePanel.editor.colorsScheme) == null || remove){
				markSet.value.remove(highlighter)
			} else markSet.value.add(highlighter)
			return true
		}
		return false
	}

	fun refreshMarkCommentHighlight(editor: EditorImpl) {
		if(glancePanel.config.enableMarker) {
			editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength) {
				markHighlightChange(it, false)
				return@processRangeHighlightersOverlappingWith true
			}
		}
		if(glancePanel.config.enableBookmarksMark){
			val manager = BookmarksManager.getInstance(editor.project)
			manager?.bookmarks?.filterIsInstance<LineBookmark>()?.filter { it.file == editor.virtualFile }?.forEach {
				markHighlightChange(BookmarkHighlightDelegate(manager.getGroups(it).singleOrNull(), it), false)
			}
		}
	}

	fun contains(it: RangeHighlighterEx) = (glancePanel.config.enableBookmarksMark &&
			CodeInsightColors.BOOKMARKS_ATTRIBUTES == it.textAttributesKey &&
			glancePanel.editor.colorsScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES).errorStripeColor != null) ||
			it.textAttributesKey?.isMarkAttributes() == true

	fun clear() {
		if(markSet.isInitialized()) {
			markSet.value.clear()
		}
	}

	@Suppress("UNCHECKED_CAST")
	inner class BookmarkHighlightDelegate(private val group: BookmarkGroup?, private val bookmark: LineBookmark): UserDataHolderBase(), RangeHighlighterEx{
		override fun <T> getUserData(key: Key<T>): T? {
			if(key == BOOK_MARK_DESC_KEY){
				return group?.getDescription(bookmark) as T
			}
			return super.getUserData(key)
		}

		override fun getStartOffset(): Int {
			val document = glancePanel.editor.document
			if(DocumentUtil.isValidLine(bookmark.line, document)){
				return document.getLineStartOffset(bookmark.line)
			}
			return -1
		}

		override fun getEndOffset(): Int {
			val document = glancePanel.editor.document
			if(DocumentUtil.isValidLine(bookmark.line, document)){
				return document.getLineEndOffset(bookmark.line)
			}
			return -1
		}

		override fun getTextAttributesKey() = Util.MARK_COMMENT_ATTRIBUTES

		override fun getTextAttributes(scheme: EditorColorsScheme?): TextAttributes? {
			val colorScheme = scheme ?: EditorColorsManager.getInstance().globalScheme
			return colorScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES)
		}

		override fun getErrorStripeMarkColor(scheme: EditorColorsScheme?): Color? {
			val colorScheme = scheme ?: EditorColorsManager.getInstance().globalScheme
			return colorScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES)?.errorStripeColor
		}

		override fun getDocument(): Document = throw UnsupportedOperationException()

		override fun isValid(): Boolean = throw UnsupportedOperationException()

		override fun setGreedyToLeft(greedy: Boolean) = throw UnsupportedOperationException()

		override fun setGreedyToRight(greedy: Boolean) = throw UnsupportedOperationException()

		override fun isGreedyToRight() = throw UnsupportedOperationException()

		override fun isGreedyToLeft() = throw UnsupportedOperationException()

		override fun dispose() = throw UnsupportedOperationException()

		override fun getLayer()= throw UnsupportedOperationException()

		override fun getTargetArea() = throw UnsupportedOperationException()

		override fun setTextAttributesKey(textAttributesKey: TextAttributesKey) = throw UnsupportedOperationException()

		override fun getLineMarkerRenderer() = throw UnsupportedOperationException()

		override fun setLineMarkerRenderer(renderer: LineMarkerRenderer?) = throw UnsupportedOperationException()

		override fun getCustomRenderer() = throw UnsupportedOperationException()

		override fun setCustomRenderer(renderer: CustomHighlighterRenderer?) = throw UnsupportedOperationException()

		override fun getGutterIconRenderer() = throw UnsupportedOperationException()

		override fun setGutterIconRenderer(renderer: GutterIconRenderer?) = throw UnsupportedOperationException()

		override fun setErrorStripeMarkColor(color: Color?) = throw UnsupportedOperationException()

		override fun getErrorStripeTooltip() = throw UnsupportedOperationException()

		override fun setErrorStripeTooltip(tooltipObject: Any?) = throw UnsupportedOperationException()

		override fun isThinErrorStripeMark() = throw UnsupportedOperationException()

		override fun setThinErrorStripeMark(value: Boolean) = throw UnsupportedOperationException()

		override fun getLineSeparatorColor() = throw UnsupportedOperationException()

		override fun setLineSeparatorColor(color: Color?) = throw UnsupportedOperationException()

		override fun setLineSeparatorRenderer(renderer: LineSeparatorRenderer?) = throw UnsupportedOperationException()

		override fun getLineSeparatorRenderer() = throw UnsupportedOperationException()

		override fun getLineSeparatorPlacement() = throw UnsupportedOperationException()

		override fun setLineSeparatorPlacement(placement: SeparatorPlacement?) = throw UnsupportedOperationException()

		override fun setEditorFilter(filter: MarkupEditorFilter) = throw UnsupportedOperationException()

		override fun getEditorFilter() = throw UnsupportedOperationException()

		override fun getId() = throw UnsupportedOperationException()

		override fun isAfterEndOfLine() = throw UnsupportedOperationException()

		override fun setAfterEndOfLine(value: Boolean) = throw UnsupportedOperationException()

		override fun getAffectedAreaStartOffset() = throw UnsupportedOperationException()

		override fun getAffectedAreaEndOffset() = throw UnsupportedOperationException()

		override fun setTextAttributes(textAttributes: TextAttributes?) = throw UnsupportedOperationException()

		override fun setVisibleIfFolded(value: Boolean) = throw UnsupportedOperationException()

		override fun isVisibleIfFolded() = throw UnsupportedOperationException()

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as BookmarkHighlightDelegate
			return bookmark == other.bookmark
		}

		override fun hashCode(): Int {
			return bookmark.hashCode()
		}
	}

	companion object{
		val BOOK_MARK_DESC_KEY: Key<String> = Key.create("bookmark.desc.key")
	}
}