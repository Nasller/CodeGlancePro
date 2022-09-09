package com.nasller.codeglance.panel.vcs

import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.Disposer
import com.nasller.codeglance.listener.MyVcsListener
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.GlancePanel.Companion.fitLineToEditor
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class MyVcsPanel(val glancePanel: GlancePanel) : JPanel(){
	init{
		Disposer.register(glancePanel.editor.disposable, MyVcsListener(this))
		val mouseHandler = MouseHandler()
		addMouseListener(mouseHandler)
		addMouseWheelListener(mouseHandler)
		addMouseMotionListener(mouseHandler)
		addMouseListener(glancePanel.myPopHandler)
		preferredSize = Dimension(8,0)
		isOpaque = false
	}

	override fun paintComponent(gfx: Graphics) = glancePanel.run {
		with(gfx as Graphics2D){ paintVcs(getVisibleRangeOffset(),this@MyVcsPanel.width) }
	}

	private inner class MouseHandler : MouseAdapter() {
		private var hoverVcsLine: Int? = null

		override fun mouseClicked(e: MouseEvent) {
			hoverVcsLine?.let {
				val editor = glancePanel.editor
				editor.caretModel.moveToVisualPosition(VisualPosition(it,0))
				editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
			}
		}

		override fun mouseMoved(e: MouseEvent) {
			val editor = glancePanel.editor
			val rangeOffset = glancePanel.getVisibleRangeOffset()
			val process = editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(rangeOffset.from,rangeOffset.to) {
				if (it.isThinErrorStripeMark) it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
					val visualLine = fitLineToEditor(editor,glancePanel.getMyRenderVisualLine(e.y + glancePanel.scrollState.visibleStart))
					val startVisual = editor.offsetToVisualLine(it.startOffset)
					if (visualLine in startVisual..editor.offsetToVisualLine(it.endOffset)) {
						cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						hoverVcsLine = startVisual
						return@processRangeHighlightersOverlappingWith false
					}
				}
				return@processRangeHighlightersOverlappingWith true
			}
			if(process) {
				cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
				hoverVcsLine = null
			}
		}

		override fun mouseExited(e: MouseEvent) {
			cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
			hoverVcsLine = null
		}
	}
}