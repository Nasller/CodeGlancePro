package com.nasller.codeglance.panel.vcs

import com.intellij.openapi.Disposable
import com.nasller.codeglance.listener.MyVcsListener
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

class MyVcsPanel(private val glancePanel: GlancePanel) : JPanel(), Disposable {
	val editor = glancePanel.editor
	private val myVcsListener = MyVcsListener(this,glancePanel)
	init{
		glancePanel.vcsRenderService?.let {
			val mouseHandler = it.getMouseHandle(glancePanel,this)
			addMouseListener(mouseHandler)
			addMouseWheelListener(mouseHandler)
			addMouseMotionListener(mouseHandler)
		}
		addMouseListener(glancePanel.myPopHandler)
		preferredSize = Dimension(vcsWidth,0)
		isOpaque = false
	}

	override fun paint(gfx: Graphics) {
		glancePanel.vcsRenderService?.paintVcs(glancePanel,gfx as Graphics2D,false)
	}

	override fun dispose() {
		myVcsListener.dispose()
	}

	companion object{
		const val vcsWidth:Int = 8
	}
}