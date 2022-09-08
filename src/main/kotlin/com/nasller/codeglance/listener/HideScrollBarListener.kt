package com.nasller.codeglance.listener

import com.intellij.util.SingleAlarm
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
		ignorePowerSaveMode()
	}
	private val alarm = SingleAlarm({ if (checkHide) start(glancePanel.width, 0) },500,glancePanel)
	private val checkHide
		get()= glancePanel.config.hoveringToShowScrollBar && !glancePanel.myPopHandler.isVisible && !glancePanel.scrollbar.hovering

	override fun mouseEntered(e: MouseEvent) {
		if(glancePanel.width == 0) start(0,glancePanel.getConfigSize().width)
	}

	fun hideGlanceRequest() = let{ if (checkHide) alarm.cancelAndRequest() }

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
					runWhenExpiredOrCancelled { if(glancePanel.width > 0 && to != 0) hideGlanceRequest() }
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
			start(glancePanel.width,0)
		}
	}

	fun removeHideScrollBarListener() = glancePanel.run {
		val scrollBarListener = this@HideScrollBarListener
		if (!config.hideOriginalScrollBar) editor.scrollPane.verticalScrollBar.removeMouseListener(scrollBarListener)
		else myVcsPanel?.removeMouseListener(scrollBarListener)
		alarm.cancel()
		animator.stop()
		showHideOriginScrollBar(true)
		refreshWithWidth(false)
	}
}