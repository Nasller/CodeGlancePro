package com.nasller.codeglance.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Alarm
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class OriginalScrollBarListener(private val glancePanel: GlancePanel):MouseAdapter(), SettingsChangeListener {
	private var hovering = false
	private val alarm = Alarm(glancePanel)
	init {
		ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
	}
	override fun mouseMoved(e: MouseEvent?) {
		hovering = true
		if(!glancePanel.isVisible){
			glancePanel.isVisible = true
			glancePanel.revalidate()
		}
	}

	override fun mouseExited(e: MouseEvent?) {
		hovering = false
		hideGlanceRequest()
	}

	override fun onHoveringOriginalScrollBarChanged(value:Boolean) {
		if(value){
			glancePanel.addOriginalScrollBarListener()
		}else{
			glancePanel.removeOriginalScrollBarListener()
		}
		glancePanel.revalidate()
	}

	fun hideGlanceRequest(){
		if (!glancePanel.config.hideOriginalScrollBar && glancePanel.config.hoveringToShowScrollBar
			&& glancePanel.isVisible && !alarm.isDisposed) {
			alarm.addRequest({
				synchronized(this) {
					if (glancePanel.config.hoveringToShowScrollBar && glancePanel.isVisible && !hovering && glancePanel.scrollbar?.hovering == false) {
						glancePanel.isVisible = false
						glancePanel.revalidate()
					}
				}
			}, 500)
		}
	}
}