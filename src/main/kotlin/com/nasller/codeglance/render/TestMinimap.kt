package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import java.awt.Color
import java.awt.Font
import java.beans.PropertyChangeEvent
import java.util.*
import kotlin.collections.set
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class TestMinimap(glancePanel: GlancePanel) : BaseMinimap(glancePanel){
	private val renderDataList = ArrayList<LineRenderData?>(Collections.nCopies(editor.visibleLineCount, null))
	init { makeListener() }

	override fun update() {
		val curImg = getMinimapImage() ?: return
		if(rangeList.size > 0) rangeList.clear()
		val markAttributes by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getAttributes(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES)
		}
		val font by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getFont(
				when (markAttributes.fontType) {
					Font.ITALIC -> EditorFontType.ITALIC
					Font.BOLD -> EditorFontType.BOLD
					Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
					else -> EditorFontType.PLAIN
				}
			).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
		}
		val text by lazy(LazyThreadSafetyMode.NONE) { editor.document.immutableCharSequence }
		val graphics = curImg.createGraphics()
		graphics.composite = GlancePanel.CLEAR
		graphics.fillRect(0, 0, curImg.width, curImg.height)
		UISettings.setupAntialiasing(graphics)
		var totalY = 0
		var skipY = 0
		for ((index, it) in renderDataList.withIndex()) {
			if(it == null) continue
			it.rebuildRange(index, totalY)
			if(skipY > 0){
				if(skipY in 1 .. it.aboveBlockLine){
					totalY += it.aboveBlockLine
					skipY = 0
				}else {
					val curY = it.aboveBlockLine + it.y
					totalY += curY
					skipY -= curY
					continue
				}
			}else {
				totalY += it.aboveBlockLine
			}
			var curX = it.startX
			var curY = totalY
			val renderCharAction = { char: Char ->
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
			when(it.lineType){
				LineType.CODE -> {
					it.renderData.forEach { renderData ->
						renderData.color.setColorRgba()
						renderData.renderStr.forEach(renderCharAction)
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
					graphics.drawString(commentText, curX,curY + (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
					skipY = (config.markersScaleFactor.toInt() - 1) * config.pixelsPerLine
				}
				LineType.CUSTOM_FOLD -> {
					it.color!!.setColorRgba()
					it.renderStr!!.forEach(renderCharAction)
				}
			}
			totalY += it.y
		}
		graphics.dispose()
	}

	private fun LineRenderData.rebuildRange(index: Int,curY: Int){
		if(lineType == LineType.CUSTOM_FOLD){
			rangeList.add(index to Range(curY, curY + y - config.pixelsPerLine + aboveBlockLine))
		}else if(aboveBlockLine > 0){
			rangeList.add(index - 1 to Range(curY, curY + y - config.pixelsPerLine + aboveBlockLine))
		}
	}

	@RequiresEdt
	private fun refreshRenderData(startVisualLine: Int = 0, endVisualLine: Int = 0) {
		if(editor.isDisposed) return
		if(startVisualLine == 0 && endVisualLine == 0) resetRenderData()
		val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
		if(visLinesIterator.atEnd()) return

		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val markCommentMap = hashMapOf<Int,RangeHighlighterEx>()
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(visLinesIterator.getVisualLineStartOffset(), editor.document.textLength){
			if(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == it.textAttributesKey){
				markCommentMap[DocumentUtil.getLineStartOffset(it.startOffset,editor.document)] = it
			}
			return@processRangeHighlightersOverlappingWith true
		}
		while (!visLinesIterator.atEnd()) {
			val visualLine = visLinesIterator.getVisualLine()
			val start = visLinesIterator.getVisualLineStartOffset()
			//BLOCK_INLAY
			val aboveBlockLine = visLinesIterator.getBlockInlaysAbove().sumOf { (it.heightInPixels * scrollState.scale).toInt() }
			//CUSTOM_FOLD
			var foldRegion = visLinesIterator.getCurrentFoldRegion()
			var foldStartOffset = foldRegion?.startOffset ?: -1
			if(foldRegion is CustomFoldRegion && foldStartOffset == start){
				val foldEndOffset = foldRegion.endOffset
				//jump over the fold line
				val heightLine = (foldRegion.heightInPixels * scrollState.scale).toInt()
				//this is render document
				val line = editor.document.getLineNumber(foldStartOffset) - 1 + (heightLine / config.pixelsPerLine)
				val renderStr = editor.document.getText(TextRange(foldStartOffset, if(DocumentUtil.isValidLine(line,editor.document)) {
					val lineEndOffset = editor.document.getLineEndOffset(line)
					if(foldEndOffset < lineEndOffset) foldEndOffset else lineEndOffset
				}else foldEndOffset))
				renderDataList[visualLine] = LineRenderData(emptyArray(),0, heightLine, aboveBlockLine, LineType.CUSTOM_FOLD, renderStr = renderStr,
					color = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT)?.foregroundColor ?: defaultColor)
			}else{
				//COMMENT
				val commentData = markCommentMap[start]
				if(commentData != null){
					renderDataList[visualLine] = LineRenderData(emptyArray(), 2, config.pixelsPerLine, aboveBlockLine,
						LineType.COMMENT, commentHighlighterEx = commentData)
				}else{
					val end = visLinesIterator.getVisualLineEndOffset()
					var foldLineIndex = visLinesIterator.getStartFoldingIndex()
					val hlIter = editor.highlighter.createIterator(start)
					val renderList = mutableListOf<RenderData>()
					while (!hlIter.atEnd() && hlIter.end <= end){
						val curStart = if(start >= hlIter.start) start else hlIter.start
						val curEnd = hlIter.end
						if(curStart == foldStartOffset){
							val foldEndOffset = foldRegion!!.endOffset
							renderList.add(RenderData(StringUtil.replace(foldRegion.placeholderText, "\n", " "),
								editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor))
							do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < foldEndOffset)
							foldRegion = visLinesIterator.getFoldRegion(++foldLineIndex)
							foldStartOffset = foldRegion?.startOffset ?: -1
						}else{
							var highlight: RangeHighlightColor? = null
							if(config.syntaxHighlight) editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(curStart,curEnd) {
								val foregroundColor = it.getTextAttributes(editor.colorsScheme)?.foregroundColor
								return@processRangeHighlightersOverlappingWith if (foregroundColor != null) {
									highlight = RangeHighlightColor(it.startOffset,it.endOffset,foregroundColor)
									false
								}else true
							}
							renderList.add(RenderData(text.substring(curStart,curEnd), highlight?.foregroundColor
								?: runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor))
							hlIter.advance()
						}
					}
					renderDataList[visualLine] = LineRenderData(renderList.toTypedArray(),
						visLinesIterator.getStartsWithSoftWrap()?.indentInColumns ?: 0,
						config.pixelsPerLine, aboveBlockLine)
				}
			}
			if(endVisualLine == 0 || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateImage()
	}

	override fun rebuildDataAndImage(directUpdate: Boolean) {
		if (directUpdate) refreshRenderData()
		else invokeLater(modalityState){ refreshRenderData() }
	}

	private fun resetRenderData(){
		renderDataList.clear()
		renderDataList.addAll(Collections.nCopies(editor.visibleLineCount, null))
	}

	/** FoldingListener */
	override fun onFoldProcessingEnd() {
		if (editor.document.isInBulkUpdate) return
		refreshRenderData()
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED != 0 && !editor.document.isInBulkUpdate) {
			val visualLine = editor.offsetToVisualLine(region.startOffset)
			refreshRenderData(visualLine, visualLine)
		}
	}

	/** InlayModel.Listener */
	override fun onAdded(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onRemoved(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) = checkinInlayAndUpdate(inlay, changeFlags)

	private fun checkinInlayAndUpdate(inlay: Inlay<*>, changeFlags: Int? = null) {
		if(editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Inlay.Placement.ABOVE_LINE
			|| !inlay.isValid || (changeFlags != null && changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0)) return
		val visualLine = editor.offsetToVisualLine(inlay.offset)
		refreshRenderData(visualLine,visualLine)
	}

	override fun onBatchModeFinish(editor: Editor) {
		if (editor.document.isInBulkUpdate) return
		refreshRenderData()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
			refreshRenderData()
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			refreshRenderData()
		}
	}

	/** MarkupModelListener */
	override fun afterAdded(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,false)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,true)

	private fun updateRangeHighlight(highlighter: RangeHighlighterEx,remove: Boolean) {
		//如果开启隐藏滚动条则忽略Vcs高亮
		val highlightChange = glancePanel.markCommentState.markCommentHighlightChange(highlighter, remove)
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || editor.foldingModel.isInBatchFoldingOperation
			|| (glancePanel.config.hideOriginalScrollBar && highlighter.isThinErrorStripeMark)) return
		if(highlightChange || EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme))) {
			val visualLine = editor.offsetToVisualLine(highlighter.startOffset)
			refreshRenderData(visualLine, visualLine)
		} else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
			repaintOrRequest(false)
		}
	}

	/** PrioritizedDocumentListener */
	private var myUpdateInProgress: Boolean = false
	private var myDocumentChangeOldEndLine = 0

	override fun beforeDocumentChange(event: DocumentEvent) {
		if (event.document.isInBulkUpdate) return
		myUpdateInProgress = true
		myDocumentChangeOldEndLine = editor.offsetToVisualLine(event.offset + event.oldLength)
	}

	override fun documentChanged(event: DocumentEvent) {
		try {
			if (event.document.isInBulkUpdate) return
			val startVisualLine = editor.offsetToVisualLine(event.offset)
			val endVisualLine = editor.offsetToVisualLine(event.offset + event.newLength)
			if(myDocumentChangeOldEndLine < endVisualLine) {
				renderDataList.addAll(myDocumentChangeOldEndLine + 1,
					Collections.nCopies(endVisualLine - myDocumentChangeOldEndLine, null))
			}else if(myDocumentChangeOldEndLine > endVisualLine) {
				renderDataList.subList(endVisualLine + 1, myDocumentChangeOldEndLine + 1).clear()
			}
			refreshRenderData(startVisualLine, endVisualLine)
		}finally {
			myUpdateInProgress = false
		}
	}

	override fun bulkUpdateFinished(document: Document) = refreshRenderData()

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		refreshRenderData()
	}

	override fun dispose() {
		super.dispose()
		renderDataList.clear()
		rangeList.clear()
	}

	private data class LineRenderData(val renderData: Array<RenderData>, val startX: Int, val y: Int, val aboveBlockLine: Int, val lineType: LineType = LineType.CODE,
									  val renderStr: String? = null, val color: Color? = null, val commentHighlighterEx: RangeHighlighterEx? = null) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as LineRenderData
			if (!renderData.contentEquals(other.renderData)) return false
			if (startX != other.startX) return false
			if (y != other.y) return false
			if (aboveBlockLine != other.aboveBlockLine) return false
			if (lineType != other.lineType) return false
			if (renderStr != other.renderStr) return false
			if (color != other.color) return false
			if (commentHighlighterEx != other.commentHighlighterEx) return false
			return true
		}

		override fun hashCode(): Int {
			var result = renderData.contentHashCode()
			result = 31 * result + startX
			result = 31 * result + y
			result = 31 * result + aboveBlockLine
			result = 31 * result + lineType.hashCode()
			result = 31 * result + (renderStr?.hashCode() ?: 0)
			result = 31 * result + (color?.hashCode() ?: 0)
			result = 31 * result + (commentHighlighterEx?.hashCode() ?: 0)
			return result
		}
	}

	private data class RenderData(val renderStr: String, val color: Color)

	private enum class LineType{ CODE, COMMENT, CUSTOM_FOLD}
}