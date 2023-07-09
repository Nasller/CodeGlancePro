package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.util.findParentOfType
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Font
import java.awt.Graphics2D
import kotlin.math.roundToInt

class MainMinimap(glancePanel: GlancePanel): BaseMinimap(glancePanel){
	private val isLogFile = editor.virtualFile?.run { fileType::class.qualifiedName?.contains("ideolog") } ?: false

	override fun update() {
		val curImg = getMinimapImage() ?: return
		val rangeMap = glancePanel.rangeMap
		if(rangeMap.size > 0) rangeMap.clear()
		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val hlIter = editor.highlighter.createIterator(0).run {
			if(isLogFile) IdeLogFileHighlightDelegate(editor.document,this) else this
		}
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled
		val hasBlockInlay = editor.inlayModel.hasBlockElements()

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
		val g = curImg.createGraphics()
		g.composite = GlancePanel.CLEAR
		g.fillRect(0, 0, curImg.width, curImg.height)
		val highlight = makeMarkHighlight(text, g)
		loop@ while (!hlIter.atEnd()) {
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
						(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).setColorRgba()
						StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
					}
					skipY -= foldLine * config.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					(color ?: defaultColor).setColorRgba()
					//jump over the fold line
					val heightLine = (region.heightInPixels * scrollState.scale).toInt()
					skipY -= (foldLine + 1) * config.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					rangeMap.compute(editor.offsetToVisualLine(endOffset)) { _,list ->
						Range(y, editor.document.getLineNumber(hlIter.start) * config.pixelsPerLine + skipY).run(mergeRangeList(list))
					}
					//this is render document
					val line = startLineNumber - 1 + (heightLine / config.pixelsPerLine)
					text.subSequence(start, if(DocumentUtil.isValidLine(line,editor.document)){
						val lineEndOffset = editor.document.getLineEndOffset(line)
						if(endOffset < lineEndOffset) endOffset else lineEndOffset
					}else endOffset).forEach(moveAndRenderChar)
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
									.sumOf { (it.heightInPixels * scrollState.scale).toInt() }
								if (sumBlock > 0) {
									rangeMap.compute(editor.offsetToVisualLine(startOffset) - 1){ _,list ->
										Range(y, y + sumBlock).run(mergeRangeList(list)) }
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
									.sumOf { (it.heightInPixels * scrollState.scale).toInt() }
								if (sumBlock > 0) {
									rangeMap.compute(editor.offsetToVisualLine(startOffset) - 1){ _,list ->
										Range(y, y + sumBlock).run(mergeRangeList(list)) }
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

	private fun makeMarkHighlight(text: CharSequence, graphics: Graphics2D):Map<Int,MarkCommentData>{
		val markCommentMap = glancePanel.markCommentState.markCommentMap
		if(markCommentMap.isNotEmpty()) {
			val lineCount = editor.document.lineCount
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
					val commentText = text.substring(startOffset, highlighterEx.endOffset)
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(attributes.fontType, font.size2D)
					} else font
					val line = editor.document.getLineNumber(textRange.startOffset) + (config.markersScaleFactor.toInt() - 1)
					val jumpEndOffset = if (lineCount <= line) text.length else {
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

	private data class MarkCommentData(var jumpEndOffset: Int,val comment: String,val font: Font,val fontHeight:Int)
}

private class IdeLogFileHighlightDelegate(private val document: Document, private val highlighterIterator: HighlighterIterator) : HighlighterIterator by highlighterIterator{
	private val length = document.textLength

	override fun getEnd(): Int {
		val end = highlighterIterator.end
		return if(DocumentUtil.isAtLineEnd(end,document) && end + 1 < length) end + 1
		else end
	}
}