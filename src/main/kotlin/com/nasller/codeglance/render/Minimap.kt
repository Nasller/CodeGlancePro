package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.util.findParentOfType
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

class Minimap(private val glancePanel: GlancePanel){
	private val editor = glancePanel.editor
	private val config = glancePanel.config
	private val scaleBuffer = FloatArray(4)
	private val isLogFile = editor.virtualFile?.run { fileType::class.qualifiedName?.contains("ideolog") } ?: false
	val markCommentMap = hashMapOf<Long,RangeHighlighterEx>()
	var img = lazy(LazyThreadSafetyMode.NONE) { getBufferedImage() }
	val rangeList = mutableListOf<Pair<Int,Range<Int>>>()

	fun update() {
		val lineCount = editor.document.lineCount
		if(lineCount <= 0) return
		var curImg = img.value
		if (curImg.height < glancePanel.scrollState.documentHeight || curImg.width < glancePanel.width) {
			curImg.flush()
			curImg = getBufferedImage()
			img = lazyOf(curImg)
		}
		if(rangeList.size > 0) rangeList.clear()
		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val hlIter = editor.highlighter.createIterator(0)
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled
		val hasBlockInlay = editor.inlayModel.hasBlockElements()

		var x = 0
		var y = 0
		var skipY = 0
		var lineNumber = 0
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
		val g = curImg.createGraphics()
		g.composite = GlancePanel.CLEAR
		g.fillRect(0, 0, curImg.width, curImg.height)
		val highlight = makeMarkHighlight(text,lineCount,g)
		loop@ while (!hlIter.atEnd() && !editor.isDisposed) {
			val start = hlIter.start
			//#69 fix log file of the ideolog plugin
			editor.document.getLineNumber(start).let {
				if(isLogFile && it != lineNumber && x > 0) {
					x = 0
					lineNumber = it
				}
				y = it * config.pixelsPerLine + skipY
			}
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
						(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).setColorRgba()
						StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
					}
					skipY -= foldLine * config.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					(color ?: defaultColor).setColorRgba()
					//jump over the fold line
					val heightLine = (region.heightInPixels * glancePanel.scrollState.scale).roundToInt()
					skipY -= (foldLine + 1) * config.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					rangeList.add(Pair(editor.offsetToVisualLine(endOffset),
						Range(y,editor.document.getLineNumber(hlIter.start) * config.pixelsPerLine + skipY)))
					//this is render document
					val line = startLineNumber - 1 + (heightLine / config.pixelsPerLine)
					text.subSequence(start, if(lineCount <= line) endOffset else {
						val lineEndOffset = editor.document.getLineEndOffset(line)
						if(endOffset < lineEndOffset) endOffset else lineEndOffset
					}).forEach(moveAndRenderChar)
				}
			} else {
				val commentData = highlight[start]
				if(commentData != null){
					g.font = commentData.font
					g.drawString(commentData.comment,2,y + commentData.fontHeight)
					if (softWrapEnable) {
						val softWraps = editor.softWrapModel.getSoftWrapsForRange(start, commentData.jumpEndOffset)
						softWraps.forEachIndexed { index, softWrap ->
							softWrap.chars.forEach {char -> moveCharIndex(char.code) { skipY += config.pixelsPerLine } }
							if (index == softWraps.size - 1){
								commentData.jumpEndOffset = DocumentUtil.getLineEndOffset(softWrap.end, editor.document)
							}
						}
					}
					while (!hlIter.atEnd() && hlIter.start < commentData.jumpEndOffset) {
						for(offset in hlIter.start until  hlIter.end) {
							moveCharIndex(text[offset].code) { if (hasBlockInlay) {
								val startOffset = offset + 1
								val lineEndOffset = DocumentUtil.getLineEndOffset(startOffset, editor.document)
								val sumBlock = editor.inlayModel.getBlockElementsInRange(startOffset, lineEndOffset)
									.filter { it.placement == Inlay.Placement.ABOVE_LINE }
									.sumOf { (it.heightInPixels * glancePanel.scrollState.scale).roundToInt() }
								if (sumBlock > 0) {
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
									y += sumBlock
									skipY += sumBlock
									commentData.jumpEndOffset = lineEndOffset
								}
							} }
						}
						hlIter.advance()
					}
				} else {
					val end = hlIter.end
					val highlightList = if(config.syntaxHighlight) getHighlightColor(start, end) else emptyList()
					for(offset in start until end) {
						// Watch out for tokens that extend past the document
						if (offset >= text.length) break@loop
						if (softWrapEnable) editor.softWrapModel.getSoftWrap(offset)?.let { softWrap ->
							softWrap.chars.forEach { moveCharIndex(it.code) { skipY += config.pixelsPerLine } }
						}
						val charCode = text[offset].code
						moveCharIndex(charCode) { if (hasBlockInlay) {
								val startOffset = offset + 1
								val sumBlock = editor.inlayModel.getBlockElementsInRange(startOffset, DocumentUtil.getLineEndOffset(startOffset, editor.document))
									.filter { it.placement == Inlay.Placement.ABOVE_LINE }
									.sumOf { (it.heightInPixels * glancePanel.scrollState.scale).roundToInt() }
								if (sumBlock > 0) {
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
									y += sumBlock
									skipY += sumBlock
								}
						}}
						curImg.renderImage(x, y, charCode) {
							(highlightList.firstOrNull {
								offset >= it.startOffset && offset < it.endOffset
							}?.foregroundColor ?: color ?: defaultColor).setColorRgba()
						}
					}
					hlIter.advance()
				}
			}
		}
		g.dispose()
	}

	private fun makeMarkHighlight(text: CharSequence,lineCount: Int,graphics: Graphics2D):Map<Int,MarkCommentData>{
		if(markCommentMap.isNotEmpty()) {
			val map = mutableMapOf<Int, MarkCommentData>()
			val file = glancePanel.psiDocumentManager.getCachedPsiFile(editor.document)
			val attributes = editor.colorsScheme.getAttributes(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES)
			val font = editor.colorsScheme.getFont(
				when (attributes.fontType) {
					Font.ITALIC -> EditorFontType.ITALIC
					Font.BOLD -> EditorFontType.BOLD
					Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
					else -> EditorFontType.PLAIN
				}
			).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
			for (highlighterEx in markCommentMap.values) {
				val startOffset = highlighterEx.startOffset
				file?.findElementAt(startOffset)?.findParentOfType<PsiComment>(false)?.let { comment ->
					val textRange = comment.textRange
					val commentText = text.subSequence(startOffset, highlighterEx.endOffset).toString()
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(attributes.fontType, font.size2D)
					} else font
					val line = editor.document.getLineNumber(textRange.startOffset) + (config.markersScaleFactor.toInt() - 1)
					val jumpEndOffset = if (lineCount <= line) {
						editor.document.getLineEndOffset(lineCount - 1)
					} else {
						editor.document.getLineEndOffset(line)
					}
					map[textRange.startOffset] = MarkCommentData(jumpEndOffset, commentText, textFont, (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
				}
			}
			graphics.color = attributes.foregroundColor ?: editor.colorsScheme.defaultForeground
			graphics.composite = GlancePanel.srcOver
			UISettings.setupAntialiasing(graphics)
			return map
		} else return emptyMap()
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

	private fun BufferedImage.renderImage(x: Int, y: Int, char: Int, consumer: (() -> Unit)? = null) {
		if (char !in 0..32 && x in 0 until width && 0 <= y && y + config.pixelsPerLine < height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(x, y, char)
			} else {
				renderAccurate(x, y, char)
			}
		}
	}

	private fun BufferedImage.renderClean(x: Int, y: Int, char: Int) {
		val weight = when (char) {
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		when (config.pixelsPerLine) {
			// Can't show whitespace between lines anymore. This looks rather ugly...
			1 -> setPixel(x, y + 1, weight * 0.6f)
			// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
			2 -> {
				setPixel(x, y, weight * 0.3f)
				setPixel(x, y + 1, weight * 0.6f)
			}
			// Three lines we make the top nearly empty, and fade the bottom a little too
			3 -> {
				setPixel(x, y, weight * 0.1f)
				setPixel(x, y + 1, weight * 0.6f)
				setPixel(x, y + 2, weight * 0.6f)
			}
			// Empty top line, Nice blend for everything else
			4 -> {
				setPixel(x, y + 1, weight * 0.6f)
				setPixel(x, y + 2, weight * 0.6f)
				setPixel(x, y + 3, weight * 0.6f)
			}
		}
	}

	private fun BufferedImage.renderAccurate(x: Int, y: Int, char: Int) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		when (config.pixelsPerLine) {
			// Can't show whitespace between lines anymore. This looks rather ugly...
			1 -> setPixel(x, y + 1, (topWeight + bottomWeight) / 2)
			// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
			2 -> {
				setPixel(x, y, topWeight * 0.5f)
				setPixel(x, y + 1, bottomWeight)
			}
			// Three lines we make the top nearly empty, and fade the bottom a little too
			3 -> {
				setPixel(x, y, topWeight * 0.3f)
				setPixel(x, y + 1, (topWeight + bottomWeight) / 2)
				setPixel(x, y + 2, bottomWeight * 0.7f)
			}
			// Empty top line, Nice blend for everything else
			4 -> {
				setPixel(x, y + 1, topWeight)
				setPixel(x, y + 2, (topWeight + bottomWeight) / 2)
				setPixel(x, y + 3, bottomWeight)
			}
		}
	}

	/**
	 * mask out the alpha component and set it to the given value.
	 * *
	 * @param alpha     alpha percent from 0-1.
	 */
	private fun BufferedImage.setPixel(x: Int, y: Int, alpha: Float) {
		scaleBuffer[3] = alpha * 0xFF
		raster.setPixel(x, y, scaleBuffer)
	}

	private fun Color.setColorRgba() {
		scaleBuffer[0] = red.toFloat()
		scaleBuffer[1] = green.toFloat()
		scaleBuffer[2] = blue.toFloat()
	}

	fun markCommentHighlightChange(highlighter: RangeHighlighterEx,remove: Boolean) : Boolean{
		return if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey){
			if(remove) markCommentMap.remove(highlighter.id)
			else markCommentMap[highlighter.id] = highlighter
			true
		} else false
	}

	fun refreshMarkCommentHighlight(editor: EditorImpl){
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(0,editor.document.textLength){
			if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markCommentMap[it.id] = it
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	@Suppress("UndesirableClassUsage")
	private fun getBufferedImage() = BufferedImage(glancePanel.getConfigSize().width, glancePanel.scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR)

	private data class MarkCommentData(var jumpEndOffset: Int,val comment: String,val font: Font,val fontHeight:Int)

	private data class RangeHighlightColor(val startOffset: Int,val endOffset: Int,val foregroundColor: Color)
}