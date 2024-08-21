package com.nasller.codeglance.render

import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.DocumentUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.Util
import io.ktor.util.collections.*

class MarkState(val glancePanel: GlancePanel) {
	private val markSet = lazy { ConcurrentSet<RangeMarker>() }

	fun getAllMarkHighlight(): Collection<RangeMarker> = if(hasMarkHighlight()) markSet.value else emptyList()

	fun hasMarkHighlight(): Boolean = markSet.isInitialized() && markSet.value.isNotEmpty()

	fun markHighlightChange(group: BookmarkGroup, bookmark: LineBookmark, remove: Boolean): RangeMarker? {
		if(glancePanel.config.enableBookmarksMark){
			val highlighter = BookmarkHighlightDelegate(group, bookmark)
			if(markHighlightChange(highlighter, remove)){
				return highlighter
			}
		}
		return null
	}

	fun markHighlightChange(highlighter: RangeMarker, remove: Boolean): Boolean {
		if((highlighter is RangeHighlighterEx && Util.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey) ||
			highlighter is BookmarkHighlightDelegate){
			if(remove) markSet.value.remove(highlighter)
			else markSet.value.add(highlighter)
			return true
		}
		return false
	}

	fun refreshMarkCommentHighlight(editor: EditorImpl) {
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0,editor.document.textLength){
			if(Util.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markSet.value += it
			}
			return@processRangeHighlightersOverlappingWith true
		}
		if(glancePanel.config.enableBookmarksMark){
			val manager = BookmarksManager.getInstance(editor.project)
			manager?.bookmarks?.filterIsInstance<LineBookmark>()?.filter { it.file == editor.virtualFile }?.forEach {
				markSet.value += BookmarkHighlightDelegate(manager.getGroups(it).singleOrNull(), it)
			}
		}
	}

	fun clear() {
		if(markSet.isInitialized()) {
			markSet.value.clear()
		}
	}

	@Suppress("UNCHECKED_CAST")
	inner class BookmarkHighlightDelegate(private val group: BookmarkGroup?, private val bookmark: LineBookmark): UserDataHolderBase(), RangeMarker{
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

		override fun getDocument(): Document = throw UnsupportedOperationException()

		override fun isValid(): Boolean = throw UnsupportedOperationException()

		override fun setGreedyToLeft(greedy: Boolean) = throw UnsupportedOperationException()

		override fun setGreedyToRight(greedy: Boolean) = throw UnsupportedOperationException()

		override fun isGreedyToRight() = throw UnsupportedOperationException()

		override fun isGreedyToLeft() = throw UnsupportedOperationException()

		override fun dispose() = throw UnsupportedOperationException()

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