package com.nasller.codeglance.render

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * A rendered minimap of a document
 */
class Minimap(private val glancePanel: GlancePanel){
	private val editor = glancePanel.editor
	private val config = glancePanel.config
	private val scrollState = glancePanel.scrollState
	var img = BufferedImage(config.width, scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR)

	fun update() {
		val lineCount = editor.document.lineCount
		if(lineCount <= 0) return
		var curImg = img
		var preBuffer : BufferedImage? = null
		if (img.height < scrollState.documentHeight || img.width < config.width) {
			preBuffer = img
			curImg = BufferedImage(config.width, scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR)
		}
		// These are just to reduce allocations. Premature optimization???
		val scaleBuffer = FloatArray(4)
		val setColorRgba = { color: Color ->
			scaleBuffer[0] = color.red.toFloat()
			scaleBuffer[1] = color.green.toFloat()
			scaleBuffer[2] = color.blue.toFloat()
			scaleBuffer[3] = color.alpha.toFloat()
		}

		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val hlIter = editor.highlighter.createIterator(0)
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled
		val hasBlockInlay = editor.inlayModel.hasBlockElements()

		var x = 0
		var y = 0
		var skipY = 0
		val myRangeList = lazy(LazyThreadSafetyMode.NONE){ mutableListOf<Pair<Int,Range<Int>>>() }
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
			curImg.renderImage(x, y, it.code, scaleBuffer)
		}
		val g = curImg.createGraphics()
		g.composite = GlancePanel.CLEAR
		g.fillRect(0, 0, curImg.width, curImg.height)
		loop@ while (!hlIter.atEnd() && !editor.isDisposed) {
			val start = hlIter.start
			y = editor.document.getLineNumber(start) * config.pixelsPerLine + skipY
			val color by lazy(LazyThreadSafetyMode.NONE){ try {
				hlIter.textAttributes.foregroundColor
			} catch (_: ConcurrentModificationException){ null } }
			val region = editor.foldingModel.getCollapsedRegionAtOffset(start)
			if (region != null) {
				val startLineNumber = editor.document.getLineNumber(region.startOffset)
				val endOffset = region.endOffset
				val foldLine = editor.document.getLineNumber(endOffset) - startLineNumber
				if(region !is CustomFoldRegionImpl){
					if(region.placeholderText.isNotBlank()) {
						setColorRgba(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor)
						StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
					}
					skipY -= foldLine * config.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					setColorRgba(color ?: defaultColor)
					//jump over the fold line
					val heightLine = (region.heightInPixels * scrollState.scale).roundToInt()
					skipY -= (foldLine + 1) * config.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					myRangeList.value.add(Pair(editor.offsetToVisualLine(endOffset),
						Range(y,editor.document.getLineNumber(hlIter.start) * config.pixelsPerLine + skipY)))
					//this is render document
					val line = startLineNumber - 1 + (heightLine / config.pixelsPerLine)
					text.subSequence(start, if(lineCount <= line) endOffset else {
						val lineEndOffset = editor.document.getLineEndOffset(line)
						if(endOffset < lineEndOffset) endOffset else lineEndOffset
					}).forEach(moveAndRenderChar)
				}
			} else {
				val end = hlIter.end
				val highlightList = getHighlightColor(start, end)
				for(offset in start until end){
					// Watch out for tokens that extend past the document
					if (offset >= text.length) break@loop
					if (softWrapEnable) editor.softWrapModel.getSoftWrap(offset)?.let { softWrap ->
						softWrap.chars.forEach { moveCharIndex(it.code){ skipY += config.pixelsPerLine } }
					}
					val charCode = text[offset].code
					moveCharIndex(charCode){ if (hasBlockInlay) {
						val startOffset = offset + 1
						val sumBlock = editor.inlayModel.getBlockElementsInRange(startOffset, DocumentUtil.getLineEndOffset(startOffset,editor.document))
							.filter { it.placement == Inlay.Placement.ABOVE_LINE }
							.sumOf { (it.heightInPixels * scrollState.scale).roundToInt() }
						if (sumBlock > 0) {
							myRangeList.value.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
							y += sumBlock
							skipY += sumBlock
						}
					} }
					curImg.renderImage(x, y, charCode, scaleBuffer){
						setColorRgba(highlightList.firstOrNull {
							offset >= it.startOffset && offset < it.endOffset
						}?.foregroundColor ?: color ?: defaultColor)
					}
				}
				hlIter.advance()
			}
		}
		g.dispose()
		preBuffer?.let {
			img = curImg
			it.flush()
		}
		if(glancePanel.myRangeList.isNotEmpty()) glancePanel.myRangeList.clear()
		if(myRangeList.isInitialized()) glancePanel.myRangeList.addAll(myRangeList.value)
	}

	private fun getHighlightColor(startOffset:Int,endOffset:Int):MutableList<RangeHighlightColor>{
		val list = mutableListOf<RangeHighlightColor>()
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(startOffset,endOffset) {
			val foregroundColor = it.getTextAttributes(editor.colorsScheme)?.foregroundColor
			if (foregroundColor != null) list.add(RangeHighlightColor(it.startOffset,it.endOffset,foregroundColor))
			return@processRangeHighlightersOverlappingWith true
		}
		return list
	}

	private fun BufferedImage.renderImage(x: Int, y: Int, char: Int, scaleBuffer: FloatArray,consumer: (()->Unit)? = null) {
		if (char !in 0..32 && x in 0 until width && 0 <= y && y + config.pixelsPerLine < height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(x, y, char, scaleBuffer)
			} else {
				renderAccurate(x, y, char, scaleBuffer)
			}
		}
	}

	private fun BufferedImage.renderClean(x: Int, y: Int, char: Int, buffer: FloatArray) {
		val weight = when (char) {
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		when (config.pixelsPerLine) {
			1 -> // Can't show whitespace between lines anymore. This looks rather ugly...
				setPixel(x, y + 1, weight * 0.6f, buffer)
			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, weight * 0.3f, buffer)
				setPixel(x, y + 1, weight * 0.6f, buffer)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, weight * 0.1f, buffer)
				setPixel(x, y + 1, weight * 0.6f, buffer)
				setPixel(x, y + 2, weight * 0.6f, buffer)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, weight * 0.6f, buffer)
				setPixel(x, y + 2, weight * 0.6f, buffer)
				setPixel(x, y + 3, weight * 0.6f, buffer)
			}
		}
	}

	private fun BufferedImage.renderAccurate(x: Int, y: Int, char: Int, buffer: FloatArray) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		when (config.pixelsPerLine) {
			1 -> // Can't show whitespace between lines anymore. This looks rather ugly...
				setPixel(x, y + 1, (topWeight + bottomWeight) / 2, buffer)
			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, topWeight * 0.5f, buffer)
				setPixel(x, y + 1, bottomWeight, buffer)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, topWeight * 0.3f, buffer)
				setPixel(x, y + 1, (topWeight + bottomWeight) / 2, buffer)
				setPixel(x, y + 2, bottomWeight * 0.7f, buffer)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, topWeight, buffer)
				setPixel(x, y + 2, (topWeight + bottomWeight) / 2, buffer)
				setPixel(x, y + 3, bottomWeight, buffer)
			}
		}
	}

	/**
	 * mask out the alpha component and set it to the given value.
	 * *
	 * @param alpha     alpha percent from 0-1.
	 */
	private fun BufferedImage.setPixel(x: Int, y: Int, alpha: Float, scaleBuffer: FloatArray) {
		scaleBuffer[3] = alpha * 0xFF
		raster.setPixel(x, y, scaleBuffer)
	}

	private data class RangeHighlightColor(val startOffset: Int,val endOffset: Int,val foregroundColor: Color)
}