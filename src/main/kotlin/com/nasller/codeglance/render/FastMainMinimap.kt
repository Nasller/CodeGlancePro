package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Attachment
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
import com.intellij.util.Alarm
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.beans.PropertyChangeEvent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class FastMainMinimap(glancePanel: GlancePanel, private val isLogFile: Boolean) : BaseMinimap(glancePanel){
	private val renderDataList = ObjectArrayList.wrap<LineRenderData>(arrayOfNulls(editor.visibleLineCount))
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
		if(!glancePanel.checkVisible()) return
		if(startVisualLine == 0 && endVisualLine == 0) resetRenderData()
		val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
		if(visLinesIterator.atEnd()) return

		val text = editor.document.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val markCommentMap = glancePanel.markCommentState.markCommentMap.values
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, editor.document) }
		while (!visLinesIterator.atEnd()) {
			val start = visLinesIterator.getVisualLineStartOffset()
			if (start >= text.length) break
			val visualLine = visLinesIterator.getVisualLine()
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
				if(markCommentMap.containsKey(start)) {
					renderDataList[visualLine] = LineRenderData(emptyArray(), 2, config.pixelsPerLine, aboveBlockLine,
						LineType.COMMENT, commentHighlighterEx = markCommentMap[start])
				}else {
					//CODE
					val end = visLinesIterator.getVisualLineEndOffset()
					var foldLineIndex = visLinesIterator.getStartFoldingIndex()
					val hlIter = editor.highlighter.run {
						if(this is EmptyEditorHighlighter) OneLineHighlightDelegate(start,end,editor.document.createLineIterator())
						else if(isLogFile) IdeLogFileHighlightDelegate(editor.document,this.createIterator(start))
						else this.createIterator(start)
					}
					val renderList = mutableListOf<RenderData>()
					do {
						val curEnd = hlIter.end
						val curStart = if(start > hlIter.start && start < curEnd) start else hlIter.start
						if(curStart == foldStartOffset){
							val foldEndOffset = foldRegion!!.endOffset
							renderList.add(RenderData(StringUtil.replace(foldRegion.placeholderText, "\n", " "),
								editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor))
							do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < foldEndOffset)
							foldRegion = visLinesIterator.getFoldRegion(++foldLineIndex)
							foldStartOffset = foldRegion?.startOffset ?: -1
						}else{
							val highlightList = if(config.syntaxHighlight) getHighlightColor(curStart, curEnd) else emptyList()
							renderList.add(RenderData(text.substring(curStart,curEnd), (highlightList.firstOrNull {
								curStart >= it.startOffset && curEnd <= it.endOffset
							}?.foregroundColor ?: runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor)))
							hlIter.advance()
						}
					}while (!hlIter.atEnd() && hlIter.start < end)
					renderDataList[visualLine] = LineRenderData(renderList.toTypedArray(),
						visLinesIterator.getStartsWithSoftWrap()?.indentInColumns ?: 0, config.pixelsPerLine, aboveBlockLine)
				}
			}
			if(endVisualLine == 0 || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateImage(canUpdate = true)
	}

	override fun rebuildDataAndImage() {
		if(canUpdate()) invokeLater(modalityState){ refreshRenderData() }
	}

	private fun resetRenderData(){
		renderDataList.clear()
		renderDataList.addAll(ObjectArrayList.wrap(arrayOfNulls(editor.visibleLineCount)))
	}

	/** FoldingListener */
	private var myFoldingChangeStartOffset = Int.MAX_VALUE
	private var myFoldingChangeEndOffset = Int.MIN_VALUE

	override fun onFoldRegionStateChange(region: FoldRegion) {
		if (editor.document.isInBulkUpdate) return
		if(region.isValid) {
			myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, region.startOffset)
			myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, region.endOffset)
		}
	}

	override fun onFoldProcessingEnd() {
		if (editor.document.isInBulkUpdate) return
		if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset) {
			val startLine = editor.offsetToVisualLine(myFoldingChangeStartOffset)
			val endLine = editor.offsetToVisualLine(myFoldingChangeEndOffset)
			val lineDiff = editor.visibleLineCount - renderDataList.size
			if (lineDiff > 0) {
				renderDataList.addAll(startLine, ObjectArrayList.wrap(arrayOfNulls(lineDiff)))
			} else if (lineDiff < 0) {
				renderDataList.removeElements(startLine, startLine - lineDiff)
			}
			refreshRenderData(startLine, endLine)
		}
		myFoldingChangeStartOffset = Int.MAX_VALUE
		myFoldingChangeEndOffset = Int.MIN_VALUE
		assertValidState()
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED != 0 && !editor.document.isInBulkUpdate) {
			val startOffset = region.startOffset
			if (editor.foldingModel.getCollapsedRegionAtOffset(startOffset) !== region) return
			val visualLine = editor.offsetToVisualLine(startOffset)
			refreshRenderData(visualLine, visualLine)
		}
	}

	/** InlayModel.SimpleAdapter */
	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
		if(editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Inlay.Placement.ABOVE_LINE
			|| !inlay.isValid || changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0) return
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

	override fun recalculationEnds() = Unit

	/** MarkupModelListener */
	private val highlighterChangeList = mutableListOf<RangeHighlighterEx>()
	private val highlightAlarm = SingleAlarm({
		val highlighterExes = highlighterChangeList.filter { it.isValid }
		if (highlighterExes.isNotEmpty()) {
			val startLine = editor.offsetToVisualLine(highlighterExes.minOf { it.startOffset })
			val endLine = editor.offsetToVisualLine(highlighterExes.maxOf { it.endOffset })
			refreshRenderData(startLine, endLine)
		}
		highlighterChangeList.clear()
	}, 500, this, Alarm.ThreadToUse.SWING_THREAD, modalityState)

	override fun afterAdded(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,false)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,true)

	private fun updateRangeHighlight(highlighter: RangeHighlighterEx,remove: Boolean) {
		//如果开启隐藏滚动条则忽略Vcs高亮
		val highlightChange = glancePanel.markCommentState.markCommentHighlightChange(highlighter, remove)
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || editor.foldingModel.isInBatchFoldingOperation
			|| (glancePanel.config.hideOriginalScrollBar && highlighter.isThinErrorStripeMark)) return
		if(highlightChange || EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme))) {
			highlighterChangeList.add(highlighter)
			highlightAlarm.cancelAndRequest()
		} else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
			repaintOrRequest(false)
		}
	}

	/** PrioritizedDocumentListener */
	private var myDocumentChangeOldEndLine = 0

	override fun beforeDocumentChange(event: DocumentEvent) {
		assertValidState()
		if (event.document.isInBulkUpdate) return
		myDocumentChangeOldEndLine = editor.offsetToVisualLine(event.offset + event.oldLength)
	}

	override fun documentChanged(event: DocumentEvent) {
		if (event.document.isInBulkUpdate) return
		val startVisualLine = editor.offsetToVisualLine(event.offset)
		val endVisualLine = editor.offsetToVisualLine(event.offset + event.newLength)
		if(myDocumentChangeOldEndLine < endVisualLine) {
			renderDataList.addAll(myDocumentChangeOldEndLine + 1,
				ObjectArrayList.wrap(arrayOfNulls(endVisualLine - myDocumentChangeOldEndLine)))
		}else if(myDocumentChangeOldEndLine > endVisualLine) {
			renderDataList.removeElements(endVisualLine + 1, myDocumentChangeOldEndLine + 1)
		}
		refreshRenderData(startVisualLine, endVisualLine)
		assertValidState()
	}

	override fun bulkUpdateFinished(document: Document) = refreshRenderData()

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		refreshRenderData()
	}

	private fun assertValidState() {
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || !glancePanel.checkVisible()) return
		if (editor.visibleLineCount != renderDataList.size) {
			LOG.error("Inconsistent state {}", Attachment("glance.txt", editor.dumpState()))
			rebuildDataAndImage()
		}
	}

	override fun dispose() {
		super.dispose()
		renderDataList.clear()
		highlighterChangeList.clear()
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

	private companion object{
		private val LOG = LoggerFactory.getLogger(FastMainMinimap::class.java)
	}
}