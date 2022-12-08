package com.nasller.codeglance.visitor

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.nasller.codeglance.config.CodeGlanceConfigService

abstract class MyRainbowVisitor : HighlightVisitor {
	private var myHolder: HighlightInfoHolder? = null

	override fun suitableForFile(file: PsiFile): Boolean {
		val config = CodeGlanceConfigService.ConfigInstance.state
		return config.enableMarker && (file.fileType.defaultExtension.isBlank() || config.disableLanguageSuffix
			.split(",").toSet().contains(file.fileType.defaultExtension).not())
	}

	override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
		myHolder = holder
		try {
			action.run()
		} catch (_:Throwable) {
		} finally {
			myHolder = null
		}
		return true
	}

	protected fun addInfo(highlightInfo: HighlightInfo?) {
		myHolder!!.add(highlightInfo)
	}

	protected fun getInfo(start: Int, end: Int, colorKey: TextAttributesKey): HighlightInfo? {
		return HighlightInfo
			.newHighlightInfo(RainbowHighlighter.RAINBOW_ELEMENT)
			.textAttributes(colorKey)
			.range(start,end).create()
	}
}