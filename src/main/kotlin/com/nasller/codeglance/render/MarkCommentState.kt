package com.nasller.codeglance.render

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel

class MarkCommentState(val glancePanel: GlancePanel) {
	val markCommentMap = hashMapOf<Long,RangeHighlighterEx>()

	fun markCommentHighlightChange(highlighter: RangeHighlighterEx, remove: Boolean) : Boolean{
		if(glancePanel.editor.editorKind == EditorKind.CONSOLE) return false
		return if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey){
			if(remove) markCommentMap.remove(highlighter.id)
			else markCommentMap[highlighter.id] = highlighter
			true
		} else false
	}

	fun refreshMarkCommentHighlight(editor: EditorImpl){
		if(editor.editorKind == EditorKind.CONSOLE) return
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0,editor.document.textLength){
			if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markCommentMap[it.id] = it
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	fun clear() = markCommentMap.clear()
}