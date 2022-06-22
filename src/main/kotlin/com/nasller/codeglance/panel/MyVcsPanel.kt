package com.nasller.codeglance.panel

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.Range
import com.nasller.codeglance.listener.MyVcsListener
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class MyVcsPanel(private val glancePanel: GlancePanel) : JPanel(), Disposable {
	val editor = glancePanel.editor
	private val defaultCursor = Cursor(Cursor.DEFAULT_CURSOR)
	private val myVcsListener = MyVcsListener(this)
	init{
		val mouseHandler = MouseHandler()
		addMouseListener(mouseHandler)
		addMouseWheelListener(mouseHandler)
		addMouseMotionListener(mouseHandler)
		addMouseListener(glancePanel.myPopHandler)
		preferredSize = Dimension(vcsWidth,0)
		isOpaque = false
	}

	override fun paint(gfx: Graphics) {
		val graphics2D = gfx as Graphics2D
		glancePanel.paintVcs(graphics2D,false)
	}

	inner class MouseHandler : MouseAdapter() {
		private var hoverVcsRange:Range? = null

		override fun mouseClicked(e: MouseEvent) {
			hoverVcsRange?.let {
				editor.caretModel.moveToLogicalPosition(LogicalPosition(it.line1,0))
				editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
			}
		}

		override fun mouseMoved(e: MouseEvent) {
			glancePanel.trackerManager?.getLineStatusTracker(editor.document)?.run {
				val logicalPosition = editor.visualToLogicalPosition(
					VisualPosition((e.y + glancePanel.scrollState.visibleStart) / glancePanel.config.pixelsPerLine, 0))
				val range = getRangeForLine(logicalPosition.line)
				if(range != null && (range !is LocalRange || range.changelistId == glancePanel.changeListManager?.defaultChangeList?.id)){
					cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
					hoverVcsRange = range
				}else{
					cursor = defaultCursor
					hoverVcsRange = null
				}
			}
		}

		override fun mouseExited(e: MouseEvent) {
			cursor = defaultCursor
			hoverVcsRange = null
		}
	}

	override fun dispose() {
		myVcsListener.dispose()
	}

	companion object{
		const val vcsWidth:Int = 8
	}
}