package com.nasller.codeglance.listener

import com.intellij.util.Alarm
import com.intellij.util.animation.JBAnimator
import com.intellij.util.animation.animation
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class HideScrollBarListener(private val glancePanel: GlancePanel) : MouseAdapter() {
	private var animationId = -1L
	private val animator = JBAnimator(glancePanel).apply {
		name = "Minimap Width Animator"
		period = 4
		ignorePowerSaveMode()
	}
	private val alarm = Alarm(glancePanel)
	private val checkHide
		get()= glancePanel.config.hoveringToShowScrollBar && glancePanel.width > 0
				&& !glancePanel.myPopHandler.isVisible && glancePanel.scrollbar.isNotHoverScrollBar()

	override fun mouseEntered(e: MouseEvent) {
		if(glancePanel.width == 0) {
			val delay = glancePanel.config.delayHoveringToShowScrollBar
			val action = { start(0, glancePanel.getConfigSize().width) }
			if(delay > 0 && !alarm.isDisposed) {
				alarm.cancelAllRequests()
				alarm.addRequest(action,delay)
			} else action.invoke()
		}
	}

	override fun mouseExited(e: MouseEvent) {
		if (glancePanel.config.delayHoveringToShowScrollBar > 0 && !animator.isRunning(animationId) && glancePanel.width == 0){
			alarm.cancelAllRequests()
		}
	}

	fun hideGlanceRequest(delay : Int = 500) {
		if (checkHide && !alarm.isDisposed) {
			alarm.cancelAllRequests()
			alarm.addRequest({if (checkHide) start(glancePanel.width, 0) },delay)
		}
	}

	private fun start(from: Int, to: Int) {
		if (from != to && !animator.isRunning(animationId)){
			animationId = animator.animate(
				animation(from, to) {
					if (glancePanel.width != it) {
						glancePanel.preferredSize = Dimension(it, 0)
						glancePanel.revalidate()
						glancePanel.repaint()
					}
				}.apply {
					duration = 300
					runWhenScheduled { showHideOriginScrollBar(to == 0) }
					runWhenExpiredOrCancelled { if(to != 0) hideGlanceRequest(1000) }
				}
			)
		}
	}

	private fun showHideOriginScrollBar(show : Boolean){
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
			if (!config.hideOriginalScrollBar) editor.scrollPane.verticalScrollBar.addMouseListener(hideScrollBarListener)
			else myVcsPanel?.addMouseListener(hideScrollBarListener)
			refreshWithWidth(false)
		}
	}

	fun removeHideScrollBarListener() = glancePanel.run {
		val scrollBarListener = this@HideScrollBarListener
		if (!config.hideOriginalScrollBar) editor.scrollPane.verticalScrollBar.removeMouseListener(scrollBarListener)
		else myVcsPanel?.removeMouseListener(scrollBarListener)
		alarm.cancelAllRequests()
		animator.stop()
		showHideOriginScrollBar(true)
		refreshWithWidth(false)
	}
}