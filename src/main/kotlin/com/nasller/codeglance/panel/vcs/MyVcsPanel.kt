package com.nasller.codeglance.panel.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
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

class MyVcsPanel(val glancePanel: GlancePanel) : JPanel(), Disposable {
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
		glancePanel.run { (gfx as Graphics2D).paintVcs(getVisibleRangeOffset()) }
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

	override fun dispose() {
		myVcsListener.dispose()
	}

	companion object{
		const val vcsWidth:Int = 8
	}
}