package com.nasller.codeglance.ui

import com.intellij.ide.IdeBundle
import com.intellij.ui.ColorChooser
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.ColorButtonBase
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.plaf.ButtonUI

class ColorButton(text: String, color: Color) :ColorButtonBase(text, color){
	init {
		preferredSize = Dimension(75, 25)
		border = BorderFactory.createEmptyBorder()
	}
	override fun doPerformAction(e: ActionEvent) {
		ColorChooser.chooseColor(this, IdeBundle.message("dialog.title.choose.color"), myColor)?.let {
			myColor = it
			text = ColorUtil.toHex(it).uppercase()
		}
	}

	override fun createUI(): ButtonUI {
		return object : ColorButtonUI() {
			override fun getArcSize(): Int = JBUIScale.scale(10)
		}
	}
}