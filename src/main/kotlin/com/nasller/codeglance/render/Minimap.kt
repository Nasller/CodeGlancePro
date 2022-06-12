package com.nasller.codeglance.render

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.nasller.codeglance.panel.AbstractGlancePanel
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * A rendered minimap of a document
 */
class Minimap(glancePanel: AbstractGlancePanel,private val scrollState: ScrollState){
	private val editor = glancePanel.editor
	private val config = glancePanel.config
	private var preBuffer:BufferedImage? = null
	var img = lazy { BufferedImage(config.width, scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR) }

	fun update() {
		if(editor.document.lineCount <= 0) return
		// These are just to reduce allocations. Premature optimization???
		val colorBuffer = FloatArray(4)
		val scaleBuffer = FloatArray(4)

		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val line = editor.document.createLineIterator()
		val hlIter = editor.highlighter.createIterator(0)
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled

		var x = 0
		var y = 0
		var foldedLines = 0
		var softWrapLines = 0
		val moveCharIndex = { code: Int ->
			when (code) {
				ENTER -> {
					x = 0
					y += config.pixelsPerLine
				}
				TAB -> x += 4
				else -> x += 1
			}
		}
		var curImg = img.value
		if (curImg.height < scrollState.documentHeight || curImg.width < config.width) {
			// Create an image that is a bit bigger then the one we need, so we don't need to re-create it again soon.
			// Documents can get big, so rather than relative sizes lets just add a fixed amount on.
			preBuffer = img.value
			curImg = BufferedImage(config.width, scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR)
		}
		val g = curImg.createGraphics()
		g.composite = AbstractGlancePanel.CLEAR
		g.fillRect(0, 0, curImg.width, curImg.height)
		loop@ while (!hlIter.atEnd()) {
			var i = hlIter.start
			line.start(i)
			y = (line.lineNumber + softWrapLines - foldedLines) * config.pixelsPerLine
			// Jump over folds
			val region = editor.foldingModel.getCollapsedRegionAtOffset(i)?.let{
				if(it.startOffset >= 0 && it.endOffset >= 0 && it !is CustomFoldRegionImpl){
					foldedLines += editor.document.getLineNumber(it.endOffset) - editor.document.getLineNumber(it.startOffset)
					i = it.endOffset
					it
				} else null
			}
			if (region != null && region.placeholderText.isNotEmpty()) {
				(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).getRGBComponents(colorBuffer)
				StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach {
					val charCode = it.code
					moveCharIndex(charCode)
					curImg.renderImage(x, y, charCode, colorBuffer, scaleBuffer)
				}
			} else {
				val color = try {
					hlIter.textAttributes.foregroundColor
				} catch (_: ConcurrentModificationException){ null }
				while (i < hlIter.end) {
					// Watch out for tokens that extend past the document... bad plugins? see issue #138
					if (i >= text.length) break@loop
					if (softWrapEnable) editor.softWrapModel.getSoftWrap(i)?.let { softWrap ->
						softWrap.chars.forEach {
							val charCode = it.code
							moveCharIndex(charCode)
							if(charCode == ENTER) softWrapLines += 1
						}
					}
					val charCode = text[i].code
					moveCharIndex(charCode)
					curImg.renderImage(x, y, charCode, colorBuffer, scaleBuffer) {
						(getHighlightColor(i) ?: color ?: defaultColor).getRGBComponents(colorBuffer)
					}
					++i
				}
			}
			do // Skip to end of fold
				hlIter.advance()
			while (!hlIter.atEnd() && hlIter.start < i)
		}
		g.dispose()
		preBuffer?.let {
			img = lazyOf(curImg)
			it.flush()
			null
		}.also { preBuffer = it }
	}

	private fun getHighlightColor(offset:Int):Color?{
		var color:Color? = null
		val list = mutableListOf<RangeHighlighterEx>()
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(max(0, offset - 1),offset) {
			if (it.errorStripeTooltip != null && it.isValid && it.getTextAttributes (editor.colorsScheme) != TextAttributes.ERASE_MARKER) {
				list.add(it)
			}
			return@processRangeHighlightersOverlappingWith true
		}
		list.apply {
			if(this.size > 1) ContainerUtil.quickSort(this,IterationState.createByLayerThenByAttributesComparator(editor.colorsScheme))
		}.forEach{
			it.getTextAttributes(editor.colorsScheme)?.foregroundColor?.run {
				color = this
				return@forEach
			}
		}
		return color
	}

	private fun BufferedImage.renderImage(
		x: Int, y: Int, char: Int, colorBuffer: FloatArray,
		scaleBuffer: FloatArray, consumer: (() -> Unit)? = null
	) {
		if (shouldRenderChar(char) && x in 0 until width && 0 <= y && y + config.pixelsPerLine < height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(x, y, char, colorBuffer, scaleBuffer)
			} else {
				renderAccurate(x, y, char, colorBuffer, scaleBuffer)
			}
		}
	}

	private fun BufferedImage.renderClean(x: Int, y: Int, char: Int, color: FloatArray, buffer: FloatArray) {
		val weight = when (char) {
			in 0..32 -> 0.0f
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		if (weight == 0.0f) return
		when (config.pixelsPerLine) {
			1 -> // Can't show whitespace between lines anymore. This looks rather ugly...
				setPixel(x, y + 1, color, weight * 0.6f, buffer)
			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, color, weight * 0.3f, buffer)
				setPixel(x, y + 1, color, weight * 0.6f, buffer)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, color, weight * 0.1f, buffer)
				setPixel(x, y + 1, color, weight * 0.6f, buffer)
				setPixel(x, y + 2, color, weight * 0.6f, buffer)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, color, weight * 0.6f, buffer)
				setPixel(x, y + 2, color, weight * 0.6f, buffer)
				setPixel(x, y + 3, color, weight * 0.6f, buffer)
			}
		}
	}

	private fun BufferedImage.renderAccurate(x: Int, y: Int, char: Int, color: FloatArray, buffer: FloatArray) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		// No point rendering non-visible characters.
		if (topWeight == 0.0f && bottomWeight == 0.0f) return
		when (config.pixelsPerLine) {
			1 -> // Can't show whitespace between lines anymore. This looks rather ugly...
				setPixel(x, y + 1, color, ((topWeight + bottomWeight) / 2.0).toFloat(), buffer)
			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, color, topWeight * 0.5f, buffer)
				setPixel(x, y + 1, color, bottomWeight, buffer)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, color, topWeight * 0.3f, buffer)
				setPixel(x, y + 1, color, ((topWeight + bottomWeight) / 2.0).toFloat(), buffer)
				setPixel(x, y + 2, color, bottomWeight * 0.7f, buffer)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, color, topWeight, buffer)
				setPixel(x, y + 2, color, ((topWeight + bottomWeight) / 2.0).toFloat(), buffer)
				setPixel(x, y + 3, color, bottomWeight, buffer)
			}
		}
	}

	/**
	 * mask out the alpha component and set it to the given value.
	 * @param rgba      Color A
	 * *
	 * @param alpha     alpha percent from 0-1.
	 */
	private fun BufferedImage.setPixel(x: Int, y: Int, rgba: FloatArray, alpha: Float, scaleBuffer: FloatArray) {
		for (i in 0..2) scaleBuffer[i] = rgba[i] * 0xFF
		scaleBuffer[3] = when {
			alpha > 1 -> rgba[3]
			else -> max(alpha, 0f)
		} * 0xFF
		raster.setPixel(x, y, scaleBuffer)
	}

	private companion object{
		const val TAB = 9
		const val ENTER = 10
		const val SPACE = 32

		fun shouldRenderChar(char:Int) = char != TAB && char != SPACE && char != ENTER
	}
}