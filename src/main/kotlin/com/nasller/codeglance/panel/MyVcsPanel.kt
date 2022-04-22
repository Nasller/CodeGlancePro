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
	private val defaultCursor = Cursor(Cursor.DEFAULT_CURSOR)
	private val myVcsListener = MyVcsListener(this)
	init{
		val mouseHandler = MouseHandler()
		addMouseListener(mouseHandler)
		addMouseWheelListener(mouseHandler)
		addMouseMotionListener(mouseHandler)
		addMouseListener(glancePanel.myPopHandler)
		addHierarchyListener(myVcsListener)
		addHierarchyBoundsListener(myVcsListener)
		glancePanel.editor.contentComponent.addComponentListener(myVcsListener)
		glancePanel.editor.document.addDocumentListener(myVcsListener,this)
		glancePanel.editor.scrollingModel.addVisibleAreaListener(myVcsListener,this)
		glancePanel.editor.foldingModel.addListener(myVcsListener,this)
		preferredSize = Dimension(8,0)
		isOpaque = false
	}

	override fun paint(gfx: Graphics?) {
		val graphics2D = gfx as Graphics2D
		glancePanel.paintVcs(graphics2D)
	}

	inner class MouseHandler : MouseAdapter() {
		private var hoverVcsRange:Range? = null

		override fun mouseClicked(e: MouseEvent) {
			hoverVcsRange?.let {
				glancePanel.editor.caretModel.moveToLogicalPosition(LogicalPosition(it.line1,0))
				glancePanel.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
			}
		}

		override fun mouseMoved(e: MouseEvent) {
			val logicalPosition = glancePanel.editor.visualToLogicalPosition(
				VisualPosition((e.y + glancePanel.scrollState.visibleStart) / glancePanel.config.pixelsPerLine, 0))
			val range = glancePanel.trackerManager.getLineStatusTracker(glancePanel.editor.document)?.getRangeForLine(logicalPosition.line)
			if(range != null && (range !is LocalRange || range.changelistId == glancePanel.changeListManager.defaultChangeList.id)){
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

	override fun dispose() {
		removeHierarchyListener(myVcsListener)
		removeHierarchyBoundsListener(myVcsListener)
		glancePanel.editor.contentComponent.removeComponentListener(myVcsListener)
	}
}