package com.nasller.codeglance.panel

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.Range
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class MyVcsPanel(private val panel: AbstractGlancePanel) : JPanel() {
	private val defaultCursor = Cursor(Cursor.DEFAULT_CURSOR)
	init{
		preferredSize = Dimension(8,0)
		isOpaque = false
		val mouseHandler = MouseHandler()
		addMouseListener(mouseHandler)
		addMouseWheelListener(mouseHandler)
		addMouseMotionListener(mouseHandler)
	}

	override fun paint(gfx: Graphics?) {
		val graphics2D = gfx as Graphics2D
		panel.paintVcs(graphics2D)
		graphics2D.dispose()
	}

	inner class MouseHandler : MouseAdapter() {
		private var hoverVcsRange:Range? = null

		override fun mouseClicked(e: MouseEvent) {
			hoverVcsRange?.let {
				panel.editor.caretModel.moveToLogicalPosition(LogicalPosition(it.line1,0))
				panel.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
			}
		}

		override fun mouseMoved(e: MouseEvent) {
			val logicalPosition = panel.editor.visualToLogicalPosition(
				VisualPosition((e.y + panel.scrollState.visibleStart) / panel.config.pixelsPerLine, 0))
			val range = panel.trackerManager.getLineStatusTracker(panel.editor.document)?.getRangeForLine(logicalPosition.line)
			if(range != null && (range !is LocalRange || range.changelistId == panel.changeListManager.defaultChangeList.id)){
				cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				hoverVcsRange = range
			}else{
				cursor = defaultCursor
				hoverVcsRange = null
			}
		}

		override fun mouseExited(e: MouseEvent) {
			cursor = defaultCursor
			hoverVcsRange = null
		}
	}
}