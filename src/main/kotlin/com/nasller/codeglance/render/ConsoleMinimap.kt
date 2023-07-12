package com.nasller.codeglance.render

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.DocumentUtil
import com.nasller.codeglance.panel.GlancePanel

class ConsoleMinimap(glancePanel: GlancePanel): BaseMinimap(glancePanel){
	override fun update() {
		val curImg = getMinimapImage() ?: return
		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val lineIter = editor.document.createLineIterator()
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled

		var x = 0
		var y = 0
		var skipY = 0
		val moveCharIndex = { code: Int,enterAction: (()->Unit)? ->
			when (code) {
				9 -> x += 4//TAB
				10 -> {//ENTER
					x = 0
					y += config.pixelsPerLine
					enterAction?.invoke()
				}
				else -> x += 1
			}
		}
		val moveAndRenderChar = { it: Char ->
			moveCharIndex(it.code,null)
			curImg.renderImage(x, y, it.code)
		}
		val g = curImg.createGraphics().apply {
			composite = GlancePanel.CLEAR
			fillRect(0, 0, curImg.width, curImg.height)
		}
		loop@ while (!lineIter.atEnd()) {
			val start = lineIter.start
			y = editor.document.getLineNumber(start) * config.pixelsPerLine + skipY
			val region = editor.foldingModel.getCollapsedRegionAtOffset(start)
			if (region != null) {
				val startLineNumber = editor.document.getLineNumber(region.startOffset)
				val endOffset = region.endOffset
				val foldLine = editor.document.getLineNumber(endOffset) - startLineNumber
				if(region.placeholderText.isNotBlank()) {
					(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).setColorRgba()
					StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
				}
				skipY -= foldLine * config.pixelsPerLine
				do lineIter.advance() while (!lineIter.atEnd() && lineIter.start < endOffset)
				if(DocumentUtil.isAtLineEnd(endOffset, editor.document)) x = 0
			} else {
				val end = lineIter.end
				val highlightList = if(config.syntaxHighlight) getHighlightColor(start, end) else emptyList()
				for(offset in start until end) {
					// Watch out for tokens that extend past the document
					if (offset >= text.length) break@loop
					if (softWrapEnable) editor.softWrapModel.getSoftWrap(offset)?.let { softWrap ->
						softWrap.chars.forEach { moveCharIndex(it.code) { skipY += config.pixelsPerLine } }
					}
					val charCode = text[offset].code
					moveCharIndex(charCode,null)
					curImg.renderImage(x, y, charCode) {
						(highlightList.firstOrNull {
							offset >= it.startOffset && offset < it.endOffset
						}?.foregroundColor ?: defaultColor).setColorRgba()
					}
				}
				lineIter.advance()
			}
		}
		g.dispose()
	}
}