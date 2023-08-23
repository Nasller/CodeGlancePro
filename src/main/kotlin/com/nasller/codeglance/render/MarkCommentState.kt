package com.nasller.codeglance.render

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.Util
import java.util.concurrent.ConcurrentHashMap

class MarkCommentState(val glancePanel: GlancePanel) {
	private val markCommentMap = lazy { ConcurrentHashMap<Long,RangeHighlighterEx>() }

	fun getAllMarkCommentHighlight(): Collection<RangeHighlighterEx> = if(hasMarkCommentHighlight()) markCommentMap.value.values else emptyList()

	fun hasMarkCommentHighlight(): Boolean = markCommentMap.isInitialized() && markCommentMap.value.isNotEmpty()

	fun markCommentHighlightChange(highlighter: RangeHighlighterEx, remove: Boolean) : Boolean{
		return if(Util.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey){
			if(remove) markCommentMap.value.remove(highlighter.id)
			else markCommentMap.value[highlighter.id] = highlighter
			true
		} else false
	}

	fun refreshMarkCommentHighlight(editor: EditorImpl){
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0,editor.document.textLength){
			if(Util.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markCommentMap.value[it.id] = it
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	fun clear() {
		if(markCommentMap.isInitialized()) {
			markCommentMap.value.clear()
		}
	}
}