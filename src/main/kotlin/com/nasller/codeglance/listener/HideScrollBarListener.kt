package com.nasller.codeglance.listener

import com.intellij.util.Alarm
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class HideScrollBarListener(private val glancePanel: GlancePanel):MouseAdapter() {
	private var hovering = false
	private val alarm = Alarm(glancePanel)
	override fun mouseMoved(e: MouseEvent?) {
		hovering = true
		glancePanel.isVisible = true
	}

	override fun mouseExited(e: MouseEvent?) {
		hovering = false
		hideGlanceRequest()
	}

	fun hideGlanceRequest(){
		if (glancePanel.config.hoveringToShowScrollBar && glancePanel.isVisible && !alarm.isDisposed && !glancePanel.myPopHandler.isVisible) {
			alarm.addRequest({
				synchronized(this) {
					if (glancePanel.config.hoveringToShowScrollBar && !glancePanel.myPopHandler.isVisible &&
						!hovering && glancePanel.scrollbar?.hovering == false) {
						glancePanel.isVisible = false
					}
				}
			}, 500)
		}
	}
}