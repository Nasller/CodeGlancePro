package com.nasller.codeglance.listener

import com.intellij.util.SingleAlarm
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class HideScrollBarListener(private val glancePanel: GlancePanel):MouseAdapter() {
	private val alarm = SingleAlarm({
		if (!glancePanel.myPopHandler.isVisible && !glancePanel.scrollbar.hovering) {
			glancePanel.isVisible = false
			showHideOriginScrollBar(true)
		}
	},500,glancePanel)
	override fun mouseMoved(e: MouseEvent) {
		if(!glancePanel.isVisible){
			cancel()
			glancePanel.isVisible = true
			showHideOriginScrollBar(false)
		}
	}

	fun hideGlanceRequest(){
		if (!alarm.isDisposed && glancePanel.config.hoveringToShowScrollBar &&
				glancePanel.isVisible && !glancePanel.myPopHandler.isVisible && !glancePanel.scrollbar.hovering) {
			alarm.cancelAndRequest()
		}
	}

	fun cancel() = alarm.cancel()

	fun showHideOriginScrollBar(show:Boolean){
		if(!glancePanel.config.hideOriginalScrollBar){
			if(show) glancePanel.editor.scrollPane.verticalScrollBar.apply {
				preferredSize = Dimension(glancePanel.originalScrollbarWidth, preferredSize.height)
			} else glancePanel.editor.scrollPane.verticalScrollBar.apply {
				preferredSize = Dimension(0, preferredSize.height)
			}
		}
	}

	fun addHideScrollBarListener() = glancePanel.run {
		if (config.hoveringToShowScrollBar && !isDisabled) {
			if (!config.hideOriginalScrollBar) {
				editor.scrollPane.verticalScrollBar.addMouseListener(hideScrollBarListener)
				editor.scrollPane.verticalScrollBar.addMouseMotionListener(hideScrollBarListener)
			} else {
				myVcsPanel?.addMouseListener(hideScrollBarListener)
				myVcsPanel?.addMouseMotionListener(hideScrollBarListener)
			}
			isVisible = false
		}
	}

	fun removeHideScrollBarListener() = glancePanel.run {
		hideScrollBarListener.apply {
			if (!config.hideOriginalScrollBar) {
				editor.scrollPane.verticalScrollBar.removeMouseListener(this)
				editor.scrollPane.verticalScrollBar.removeMouseMotionListener(this)
			} else {
				myVcsPanel?.removeMouseListener(this)
				myVcsPanel?.removeMouseMotionListener(this)
			}
			cancel()
			showHideOriginScrollBar(true)
		}
		isVisible = !isDisabled
	}
}