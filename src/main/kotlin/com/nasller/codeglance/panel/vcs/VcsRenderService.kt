package com.nasller.codeglance.panel.vcs

import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.nasller.codeglance.panel.AbstractGlancePanel
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class VcsRenderService(project: Project) {
	val trackerManager = LineStatusTrackerManager.getInstance(project)
	val changeListManager = ChangeListManager.getInstance(project)

	fun getMouseHandle(glancePanel: GlancePanel, myVcsPanel: MyVcsPanel): MouseAdapter = object: MouseAdapter() {
		private var hoverVcsRange: Range? = null

		override fun mouseClicked(e: MouseEvent) {
			hoverVcsRange?.let {
				glancePanel.editor.caretModel.moveToLogicalPosition(LogicalPosition(it.line1,0))
				glancePanel.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
			}
		}

		override fun mouseMoved(e: MouseEvent) {
			glancePanel.vcsRenderService?.let{
				it.trackerManager.getLineStatusTracker(glancePanel.editor.document)?.run {
					val visualPosition = VisualPosition((e.y + glancePanel.scrollState.visibleStart) / glancePanel.config.pixelsPerLine, 0)
					val logicalPosition = glancePanel.editor.visualToLogicalPosition(visualPosition)
					val range = getRangeForLine(logicalPosition.line) ?: getNextRange(logicalPosition.line)?.let { range ->
						if(glancePanel.editor.logicalToVisualPosition(LogicalPosition(range.line1,0)).line == visualPosition.line &&
							glancePanel.editor.foldingModel.isOffsetCollapsed(glancePanel.editor.document.getLineStartOffset(range.line1)))range
						else null
					}
					if(range != null && (range !is LocalRange || range.changelistId == it.changeListManager.defaultChangeList.id)){
						myVcsPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						hoverVcsRange = range
					}else{
						myVcsPanel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
						hoverVcsRange = null
					}
				}
			}
		}

		override fun mouseExited(e: MouseEvent) {
			myVcsPanel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
			hoverVcsRange = null
		}
	}

	fun paintVcs(glancePanel: AbstractGlancePanel,g: Graphics2D, notPaint:Boolean) {
		if(notPaint) return
		glancePanel.run {
			trackerManager.getLineStatusTracker(editor.document)?.getRanges()?.run {
				g.composite = if(config.hideOriginalScrollBar) AbstractGlancePanel.srcOver else AbstractGlancePanel.srcOver0_4
				val foldRegions = editor.foldingModel.allFoldRegions.filter { fold -> fold !is CustomFoldRegionImpl && !fold.isExpanded }
				forEach {
					if (it !is LocalRange || it.changelistId == changeListManager.defaultChangeList.id) {
						try {
							g.color = LineStatusMarkerDrawUtil.getGutterColor(it.type, editor)
							val documentLine = getDocumentRenderLine(it.line1, it.line2)
							var visualLine1 = EditorUtil.logicalToVisualLine(editor, it.line1)
							var visualLine2 = EditorUtil.logicalToVisualLine(editor, it.line2)
							foldRegions.forEach { fold ->
								if (editor.document.getLineNumber(fold.startOffset) <= it.line1 &&
									it.line2 <= editor.document.getLineNumber(fold.endOffset)
								) visualLine2 = visualLine1 + 1
							}
							if (it.line1 != it.line2 && visualLine1 == visualLine2) {
								val realLine = editor.visualToLogicalPosition(VisualPosition(visualLine1, 0)).line
								visualLine1 += it.line1 - realLine
								visualLine2 += it.line2 - realLine
							}
							val start = (visualLine1 + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
							val end = (visualLine2 + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
							if(start >= 0 || end >= 0) {
								g.fillRect(0, start, width, config.pixelsPerLine)
								g.fillRect(0, start + config.pixelsPerLine, width, end - start - config.pixelsPerLine)
							}
						}catch (_:ConcurrentModificationException){}
					}
				}
			}
		}
	}
}