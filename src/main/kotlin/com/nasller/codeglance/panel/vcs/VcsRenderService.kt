package com.nasller.codeglance.panel.vcs

import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.nasller.codeglance.panel.AbstractGlancePanel
import java.awt.Graphics2D

class VcsRenderService(project: Project) {
	val trackerManager = LineStatusTrackerManager.getInstance(project)
	val changeListManager = ChangeListManager.getInstance(project)

	fun paintVcs(glancePanel: AbstractGlancePanel,g: Graphics2D, notPaint:Boolean) {
		if(notPaint) return
		glancePanel.run {
			trackerManager.getLineStatusTracker(editor.document)?.getRanges()?.run {
				val srcOver = if(config.hideOriginalScrollBar) AbstractGlancePanel.srcOver else AbstractGlancePanel.srcOver0_4
				g.composite = srcOver
				val foldRegions = editor.foldingModel.allFoldRegions.filter { fold -> fold !is CustomFoldRegionImpl && !fold.isExpanded &&
						fold.startOffset >= 0 && fold.endOffset >= 0 }
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