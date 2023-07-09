package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.view.VisualLinesIterator
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import org.jetbrains.kotlin.ir.descriptors.IrAbstractDescriptorBasedFunctionFactory.Companion.offset
import java.awt.Color
import java.awt.Font
import java.util.*
import kotlin.collections.set
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class TestMinimap(glancePanel: GlancePanel) : BaseMinimap(glancePanel) {
	private val renderDataMap = TreeMap<Int,LineRenderData>(Int::compareTo)

	override fun update() {
		val curImg = getMinimapImage() ?: return
		val markAttributes = editor.colorsScheme.getAttributes(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES)
		val font by lazy {
			editor.colorsScheme.getFont(
				when (markAttributes.fontType) {
					Font.ITALIC -> EditorFontType.ITALIC
					Font.BOLD -> EditorFontType.BOLD
					Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
					else -> EditorFontType.PLAIN
				}
			).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
		}
		val text = editor.document.immutableCharSequence
		val graphics = curImg.createGraphics()
		graphics.composite = GlancePanel.CLEAR
		graphics.fillRect(0, 0, curImg.width, curImg.height)
		UISettings.setupAntialiasing(graphics)
		var totalY = 0
		var skipLine = 0
		for (it in renderDataMap.values) {
			if(skipLine > 0){
				skipLine--
				continue
			}
			when(it.lineType){
				LineType.CODE -> {
					it.renderX.forEach { renderX ->
						renderX.color.setColorRgba()
						var curX = renderX.xStart
						text.subSequence(renderX.xStart, renderX.xEnd).forEach { char ->
							curX += when (char.code) {
								9 -> 4 //TAB
								else -> 1
							}
							curImg.renderImage(curX, totalY, char.code)
						}
					}
				}
				LineType.COMMENT -> {
					graphics.composite = GlancePanel.srcOver
					graphics.color = markAttributes.foregroundColor
					val commentText = text.substring(it.commentHighlighterEx!!.startOffset, it.commentHighlighterEx.endOffset)
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(markAttributes.fontType, font.size2D)
					} else font
					graphics.font = textFont
					graphics.drawString(commentText,2,totalY + (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
					//skip line
					skipLine = config.markersScaleFactor.toInt() - 1
				}
				LineType.CUSTOM_FOLD -> {
					it.color!!.setColorRgba()
					var curX = 0
					var curY = totalY
					it.renderStr!!.forEach {char ->
						when (char.code) {
							9 -> curX += 4 //TAB
							10 -> {//ENTER
								curX = 0
								curY += config.pixelsPerLine
							}

							else -> curX += 1
						}
						curImg.renderImage(curX, curY, char.code)
					}
				}
			}
			totalY += it.y
		}
		graphics.dispose()
	}

	fun refreshRenderData(startVisualLine: Int = 0, onlyLine: Boolean = false){
		val visLinesIterator = VisualLinesIterator(editor, startVisualLine)
		if(visLinesIterator.atEnd()) return

		if(startVisualLine == 0) renderDataMap.clear()
		if(rangeMap.size > 0 && startVisualLine == 0) rangeMap.clear()
		val defaultColor = editor.colorsScheme.defaultForeground
		val softWraps = editor.softWrapModel.registeredSoftWraps
		val markCommentMap = hashMapOf<Int,RangeHighlighterEx>()
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(visLinesIterator.visualLineStartOffset, editor.document.textLength){
			if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markCommentMap[DocumentUtil.getLineStartOffset(it.startOffset,editor.document)] = it
			}
			return@processRangeHighlightersOverlappingWith true
		}
		var curY = if(renderDataMap.isEmpty() || startVisualLine == 0) 0
		else renderDataMap.subMap(0, startVisualLine).values.sumOf { it.y }
		while (!visLinesIterator.atEnd()) {
			val visualLine = visLinesIterator.visualLine
			//BLOCK_INLAY
			val aboveBlockLine = visLinesIterator.blockInlaysAbove.sumOf { (it.heightInPixels * scrollState.scale).toInt() }.apply { if(this > 0) {
					rangeMap.compute(visualLine - 1){ _,list -> Range(curY, curY + this).run(mergeRangeList(list)) }
					curY += this
			} }
			//CUSTOM_FOLD
			val customFoldRegion = visLinesIterator.customFoldRegion
			if(customFoldRegion != null){
				val startOffset = customFoldRegion.startOffset
				val endOffset = customFoldRegion.endOffset
				val hlIter = editor.highlighter.createIterator(startOffset)
				val color = runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor
				//jump over the fold line
				val heightLine = (customFoldRegion.heightInPixels * scrollState.scale).toInt()
				rangeMap.compute(visualLine){ _,list -> Range(curY,curY + heightLine).run(mergeRangeList(list)) }
				curY += heightLine
				//this is render document
				val line = visLinesIterator.startLogicalLine + (heightLine / config.pixelsPerLine)
				val renderStr = editor.document.getText(TextRange(startOffset, if(DocumentUtil.isValidLine(line,editor.document)) endOffset else {
					val lineEndOffset = editor.document.getLineEndOffset(line)
					if(endOffset < lineEndOffset) endOffset else lineEndOffset
				}))
				renderDataMap[visualLine] = LineRenderData(emptyArray(), heightLine + aboveBlockLine, LineType.CUSTOM_FOLD, renderStr = renderStr, color = color)
			}else{
				val start = visLinesIterator.visualLineStartOffset
				//COMMENT
				val commentData = markCommentMap[start]
				if(commentData != null){
					renderDataMap[visualLine] = LineRenderData(emptyArray(), config.pixelsPerLine + aboveBlockLine, LineType.COMMENT, commentHighlighterEx = commentData)
				}else{
					var x = if (visLinesIterator.startsWithSoftWrap()) {
						softWraps[visLinesIterator.startOrPrevWrapIndex].indentInColumns
					}else 0
					val end = visLinesIterator.visualLineEndOffset
					val hlIter = editor.highlighter.createIterator(start)
					val xRenderDataList = mutableListOf<XRenderData>()
					while (!hlIter.atEnd() && hlIter.end <= end){
						val curStart = hlIter.start
						val curEnd = hlIter.end
						val xEnd = x + (curEnd - curStart)
						val highlightList = if(config.syntaxHighlight) getHighlightColor(curStart, curEnd) else emptyList()
						xRenderDataList.add(XRenderData(x, xEnd, (highlightList.firstOrNull {
							offset >= it.startOffset && offset < it.endOffset
						}?.foregroundColor ?: runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor)))
						x = xEnd + 1
						hlIter.advance()
					}
					renderDataMap[visualLine] = LineRenderData(xRenderDataList.toTypedArray(), config.pixelsPerLine + aboveBlockLine)
				}
				curY += config.pixelsPerLine
			}
			if(onlyLine) break
			else visLinesIterator.advance()
		}
	}
}

private data class LineRenderData(val renderX: Array<XRenderData>, val y: Int, val lineType: LineType = LineType.CODE,
                          val renderStr: String? = null, val color: Color? = null, val commentHighlighterEx: RangeHighlighterEx? = null) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as LineRenderData

		if (!renderX.contentEquals(other.renderX)) return false
		if (y != other.y) return false
		if (lineType != other.lineType) return false
		if (renderStr != other.renderStr) return false
		if (color != other.color) return false
		return commentHighlighterEx == other.commentHighlighterEx
	}

	override fun hashCode(): Int {
		var result = renderX.contentHashCode()
		result = 31 * result + y
		result = 31 * result + lineType.hashCode()
		result = 31 * result + (renderStr?.hashCode() ?: 0)
		result = 31 * result + (color?.hashCode() ?: 0)
		result = 31 * result + (commentHighlighterEx?.hashCode() ?: 0)
		return result
	}
}

private data class XRenderData(val xStart: Int, val xEnd: Int, val color: Color)

private enum class LineType{ CODE, COMMENT, CUSTOM_FOLD}