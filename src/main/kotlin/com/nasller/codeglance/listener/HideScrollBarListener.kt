package com.nasller.codeglance.listener

import com.intellij.util.Alarm
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class HideScrollBarListener(private val glancePanel: GlancePanel):MouseAdapter() {
	private var hovering = false
	private val alarm = Alarm(glancePanel)
	override fun mouseMoved(e: MouseEvent?) {
		hovering = true
		if(!glancePanel.isDisabled){
			glancePanel.isVisible = true
			showHideOriginScrollBar(false)
		}
	}

	override fun mouseExited(e: MouseEvent?) {
		hovering = false
		hideGlanceRequest()
	}

	fun hideGlanceRequest(){
		if (!glancePanel.isDisabled && glancePanel.config.hoveringToShowScrollBar && !alarm.isDisposed &&
			glancePanel.isVisible && !glancePanel.myPopHandler.isVisible) {
			alarm.addRequest({
				if (!glancePanel.myPopHandler.isVisible && !hovering && glancePanel.scrollbar?.hovering == false) {
					glancePanel.isVisible = false
					showHideOriginScrollBar(true)
				}
			}, 500)
		}
	}

	fun cancelAllRequest() = alarm.cancelAllRequests()

	fun showHideOriginScrollBar(show:Boolean){
		if(!glancePanel.config.hideOriginalScrollBar){
			if(show) glancePanel.editor.scrollPane.verticalScrollBar.run {
				this.preferredSize = Dimension(glancePanel.originalScrollbarWidth, this.preferredSize.height)
			}
			else glancePanel.editor.scrollPane.verticalScrollBar.run {
				this.preferredSize = Dimension(0, this.preferredSize.height)
			}
		}
	}
}