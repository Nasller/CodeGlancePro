package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ReadAction
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
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentEventUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.MathUtil
import com.intellij.util.Range
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import java.lang.reflect.Proxy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class FastMainMinimap(glancePanel: GlancePanel, virtualFile: VirtualFile?) : BaseMinimap(glancePanel, virtualFile){
	private val myDocument = editor.document
	private val renderDataList = ObjectArrayList<LineRenderData>().also {
		it.addAll(ObjectArrayList.wrap(arrayOfNulls(editor.visibleLineCount)))
	}
	private val mySoftWrapChangeListener = Proxy.newProxyInstance(platformClassLoader, softWrapListenerClass) { _, method, args ->
		return@newProxyInstance if(HOOK_METHOD == method.name && args?.size == 1){
			 onSoftWrapRecalculationEnd(args[0] as IncrementalCacheUpdateEvent)
		}else null
	}.also { editor.softWrapModel.applianceManager.addSoftWrapListener(it) }
	private val imgArray = arrayOf(getBufferedImage(), getBufferedImage())
	private var myLastImageIndex = 0
	private var myRenderDirty = false
	init { makeListener() }

	override fun getImageOrUpdate(): BufferedImage {
		return imgArray[myLastImageIndex]
	}

	override fun updateImage(canUpdate: Boolean){
		if (canUpdate) {
			if(lock.compareAndSet(false,true)) {
				val renderDataIterable = renderDataList.toList().withIndex()
				glancePanel.psiDocumentManager.performForCommittedDocument(editor.document) {
					ReadAction.nonBlocking<MutableList<Pair<Int, Range<Int>>>?>{
						update(renderDataIterable).also {
							myLastImageIndex = if(myLastImageIndex == 0) 1 else 0
						}
					}.finishOnUiThread(modalityState) {
						if(it?.isNotEmpty() == true) rangeList = it
						lock.set(false)
						glancePanel.repaint()
						if(myRenderDirty) {
							myRenderDirty = false
							updateImage()
						}
					}.submit(AppExecutorUtil.getAppExecutorService())
				}
			}else myRenderDirty = true
		}
	}

	override fun rebuildDataAndImage() {
		if(canUpdate()) invokeLater(modalityState){ resetRenderData() }
	}

	private fun getMinimapImage(): BufferedImage? {
		val index = if(myLastImageIndex == 0) 1 else 0
		var curImg = imgArray[index]
		if (curImg.height < scrollState.documentHeight || curImg.width < glancePanel.width) {
			curImg.flush()
			curImg = getBufferedImage()
			imgArray[index] = curImg
		}
		return if(editor.isDisposed || editor.document.lineCount <= 0) return null else curImg
	}

	private fun update(renderDataArray: Iterable<IndexedValue<LineRenderData?>>): MutableList<Pair<Int, Range<Int>>>?{
		val curImg = getMinimapImage() ?: return null
		val graphics = curImg.createGraphics()
		graphics.composite = GlancePanel.CLEAR
		graphics.fillRect(0, 0, curImg.width, curImg.height)
		val markAttributes by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getAttributes(CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES).also {
				UISettings.setupAntialiasing(graphics)
			}
		}
		val font by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getFont(when (markAttributes.fontType) {
				Font.ITALIC -> EditorFontType.ITALIC
				Font.BOLD -> EditorFontType.BOLD
				Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
				else -> EditorFontType.PLAIN
			}).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
		}
		var totalY = 0
		var skipY = 0
		val curRangeList = mutableListOf<Pair<Int, Range<Int>>>()
		for ((index, it) in renderDataArray) {
			if(it == null) continue
			//Coordinates
			if(it.lineType == LineType.CUSTOM_FOLD){
				curRangeList.add(index to Range(totalY, totalY + it.y - config.pixelsPerLine + it.aboveBlockLine))
			}else if(it.aboveBlockLine > 0){
				curRangeList.add(index - 1 to Range(totalY, totalY + it.y - config.pixelsPerLine + it.aboveBlockLine))
			}
			//Skipping
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
			//Rendering
			when(it.lineType){
				LineType.CODE, LineType.CUSTOM_FOLD -> {
					var curX = it.startX
					var curY = totalY
					it.renderData.forEach { renderData ->
						renderData.color.setColorRgba()
						renderData.renderCode.forEach { code: Int ->
							curImg.renderImage(curX, curY, code)
							when (code) {
								9 -> curX += 4 //TAB
								10 -> {//ENTER
									curX = 0
									curY += config.pixelsPerLine
								}
								else -> curX += 1
							}
						}
					}
				}
				LineType.COMMENT -> {
					graphics.composite = GlancePanel.srcOver
					graphics.color = markAttributes.foregroundColor
					val commentText = myDocument.getText(TextRange(it.commentHighlighterEx!!.startOffset, it.commentHighlighterEx.endOffset))
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(markAttributes.fontType, font.size2D)
					} else font
					graphics.font = textFont
					graphics.drawString(commentText, it.startX,totalY + (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
					skipY = (config.markersScaleFactor.toInt() - 1) * config.pixelsPerLine
				}
			}
			totalY += it.y
		}
		graphics.dispose()
		return curRangeList
	}

	private fun refreshRenderData(startVisualLine: Int, endVisualLine: Int) {
		if(!glancePanel.checkVisible()) return
		val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
		val text = myDocument.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val docComment by lazy(LazyThreadSafetyMode.NONE){
			editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor
		}
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, myDocument) }
		while (!visLinesIterator.atEnd()) {
			val start = visLinesIterator.getVisualLineStartOffset()
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
				val line = myDocument.getLineNumber(foldStartOffset) - 1 + (heightLine / config.pixelsPerLine)
				renderDataList[visualLine] = LineRenderData(arrayOf(RenderData(text.subSequence(foldStartOffset,
					if(DocumentUtil.isValidLine(line, myDocument)) {
						val lineEndOffset = myDocument.getLineEndOffset(line)
						if(foldEndOffset < lineEndOffset) foldEndOffset else lineEndOffset
					}else foldEndOffset).toIntArray(), docComment ?: defaultColor)),
					0, heightLine, aboveBlockLine, LineType.CUSTOM_FOLD)
			}else {
				//COMMENT
				if(markCommentMap.containsKey(start)) {
					renderDataList[visualLine] = LineRenderData(emptyArray(), 2, config.pixelsPerLine, aboveBlockLine,
						LineType.COMMENT, commentHighlighterEx = markCommentMap[start])
				}else {
					val end = visLinesIterator.getVisualLineEndOffset()
					var foldLineIndex = visLinesIterator.getStartFoldingIndex()
					val hlIter = editor.highlighter.run {
						myDocument.createLineIterator()
						if(this is EmptyEditorHighlighter) OneLineHighlightDelegate(start, end, text.subSequence(start, end))
						else if(isLogFile) IdeLogFileHighlightDelegate(myDocument, createIterator(start))
						else createIterator(start)
					}
					val renderList = mutableListOf<RenderData>()
					do {
						val curEnd = hlIter.end
						var curStart = if(start > hlIter.start && start < curEnd) start else hlIter.start
						//FOLD
						if(curStart == foldStartOffset){
							val foldEndOffset = foldRegion!!.endOffset
							renderList.add(RenderData(StringUtil.replace(foldRegion.placeholderText, "\n", " ").toIntArray(),
								editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor))
							foldRegion = visLinesIterator.getFoldRegion(++foldLineIndex)
							foldStartOffset = foldRegion?.startOffset ?: -1
							//case on fold InLine
							if(foldEndOffset < curEnd){
								curStart = foldEndOffset
							}else {
								do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < foldEndOffset)
								continue
							}
						}
						//CODE
						val renderStr = text.subSequence(curStart, curEnd)
						if(renderStr.isBlank()) {
							renderList.add(RenderData(renderStr.toIntArray(), defaultColor))
						}else{
							val highlightList = getHighlightColor(curStart, curEnd)
							if(highlightList.isNotEmpty()){
								if(highlightList.size == 1 && highlightList.first().run{ startOffset == curStart && endOffset == curEnd }){
									renderList.add(RenderData(renderStr.toIntArray(), highlightList.first().foregroundColor))
								}else {
									val lexerColor = runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor
									var nextOffset = curStart
									var preColor: Color? = null
									for(offset in curStart .. curEnd){
										val color = highlightList.firstOrNull {
											offset >= it.startOffset && offset < it.endOffset
										}?.foregroundColor ?: lexerColor
										if(preColor != null && preColor !== color){
											renderList.add(RenderData(text.subSequence(nextOffset, offset).toIntArray(), preColor))
											nextOffset = offset
										}
										preColor = color
									}
									if(nextOffset < curEnd){
										renderList.add(RenderData(text.subSequence(nextOffset, curEnd).toIntArray(), preColor ?: lexerColor))
									}
								}
							}else {
								renderList.add(RenderData(renderStr.toIntArray(), runCatching {
									hlIter.textAttributes.foregroundColor
								}.getOrNull() ?: defaultColor))
							}
						}
						hlIter.advance()
					}while (!hlIter.atEnd() && hlIter.start < end)
					renderDataList[visualLine] = LineRenderData(renderList.toTypedArray(),
						visLinesIterator.getStartsWithSoftWrap()?.indentInColumns ?: 0, config.pixelsPerLine, aboveBlockLine)
				}
			}
			if(endVisualLine == 0 || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateImage(true)
	}

	private fun resetRenderData(){
		doInvalidateRange(0, myDocument.textLength)
	}

	private var myDirty = false
	private var myFoldingChangeStartOffset = Int.MAX_VALUE
	private var myFoldingChangeEndOffset = Int.MIN_VALUE
	private var myDuringDocumentUpdate = false
	private var myDocumentChangeStartOffset = 0
	private var myDocumentChangeEndOffset = 0
	/** PrioritizedDocumentListener */
	override fun beforeDocumentChange(event: DocumentEvent) {
		assertValidState()
		myDuringDocumentUpdate = true
		if (event.document.isInBulkUpdate) return
		val offset = event.offset
		val moveOffset = if (DocumentEventUtil.isMoveInsertion(event)) event.moveOffset else offset
		myDocumentChangeStartOffset = min(offset, moveOffset)
		myDocumentChangeEndOffset = max(offset, moveOffset) + event.newLength
	}

	override fun documentChanged(event: DocumentEvent) {
		myDuringDocumentUpdate = false
		if (event.document.isInBulkUpdate) return
		doInvalidateRange(myDocumentChangeStartOffset, myDocumentChangeEndOffset)
		assertValidState()
	}

	override fun bulkUpdateFinished(document: Document) = resetRenderData()

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	/** FoldingListener */
	override fun onFoldRegionStateChange(region: FoldRegion) {
		if (myDocument.isInBulkUpdate) return
		if(region.isValid) {
			myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, region.startOffset)
			myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, region.endOffset)
		}
	}

	override fun beforeFoldRegionDisposed(region: FoldRegion) {
		if (!myDuringDocumentUpdate || myDocument.isInBulkUpdate || region !is CustomFoldRegion) return
		myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, region.getStartOffset())
		myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, region.getEndOffset())
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED == 0 || myDocument.isInBulkUpdate || checkDirty()) return
		val startOffset = region.startOffset
		if (editor.foldingModel.getCollapsedRegionAtOffset(startOffset) !== region) return
		val visualLine = editor.offsetToVisualLine(startOffset)
		refreshRenderData(visualLine, visualLine)
	}

	override fun onFoldProcessingEnd() {
		if (myDocument.isInBulkUpdate) return
		if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset) {
			doInvalidateRange(myFoldingChangeStartOffset, myFoldingChangeEndOffset)
		}
		myFoldingChangeStartOffset = Int.MAX_VALUE
		myFoldingChangeEndOffset = Int.MIN_VALUE
		assertValidState()
	}

	/** InlayModel.SimpleAdapter */
	override fun onAdded(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onRemoved(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) = checkinInlayAndUpdate(inlay, changeFlags)

	private fun checkinInlayAndUpdate(inlay: Inlay<*>, changeFlags: Int? = null) {
		if(myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Inlay.Placement.ABOVE_LINE
			|| !inlay.isValid || (changeFlags != null && changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0)) return
		val offset = inlay.offset
		doInvalidateRange(offset,offset)
	}

	override fun onBatchModeFinish(editor: Editor) {
		if (myDocument.isInBulkUpdate) return
		resetRenderData()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
			resetRenderData()
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			resetRenderData()
		}
	}

	override fun recalculationEnds() = Unit

	private fun onSoftWrapRecalculationEnd(event: IncrementalCacheUpdateEvent) {
		if (myDocument.isInBulkUpdate) return
		var invalidate = true
		if (editor.foldingModel.isInBatchFoldingOperation) {
			myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, event.startOffset)
			myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, event.actualEndOffset)
			invalidate = false
		}
		if (myDuringDocumentUpdate) {
			myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, event.startOffset)
			myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, event.actualEndOffset)
			invalidate = false
		}
		if (invalidate) {
			doInvalidateRange(event.startOffset, event.actualEndOffset)
		}
	}

	/** MarkupModelListener */
	override fun afterAdded(highlighter: RangeHighlighterEx) {
		glancePanel.markCommentState.markCommentHighlightChange(highlighter, false)
		updateRangeHighlight(highlighter)
	}

	override fun beforeRemoved(highlighter: RangeHighlighterEx) {
		glancePanel.markCommentState.markCommentHighlightChange(highlighter, true)
	}

	override fun afterRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

	private fun updateRangeHighlight(highlighter: RangeHighlighterEx) {
		EdtInvocationManager.invokeLaterIfNeeded {
			if (!glancePanel.checkVisible() || myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode ||
				editor.foldingModel.isInBatchFoldingOperation || myDuringDocumentUpdate) return@invokeLaterIfNeeded
			if(highlighter.isThinErrorStripeMark.not() && (CodeGlanceColorsPage.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey ||
						EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))) {
				val textLength = myDocument.textLength
				val startOffset = MathUtil.clamp(highlighter.affectedAreaStartOffset, 0, textLength)
				val endOffset = MathUtil.clamp(highlighter.affectedAreaEndOffset, 0, textLength)
				if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) return@invokeLaterIfNeeded
				invalidateRange(startOffset, endOffset)
			}else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
				glancePanel.repaint()
			}
		}
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName) return
		resetRenderData()
	}

	private fun invalidateRange(startOffset: Int, endOffset: Int) {
		if(myDuringDocumentUpdate) {
			myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, startOffset)
			myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, endOffset)
		}else if (myFoldingChangeEndOffset != Int.MIN_VALUE) {
			myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, startOffset)
			myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, endOffset)
		}else {
			doInvalidateRange(startOffset, endOffset)
		}
	}

	private fun doInvalidateRange(startOffset: Int, endOffset: Int) {
		if (checkDirty()) return
		val startVisualLine = editor.offsetToVisualLine(startOffset, false)
		val endVisualLine = editor.offsetToVisualLine(endOffset, true)
		val lineDiff = editor.visibleLineCount - renderDataList.size
		if (lineDiff > 0) {
			renderDataList.addAll(startVisualLine, ObjectArrayList.wrap(arrayOfNulls(lineDiff)))
		}else if (lineDiff < 0) {
			renderDataList.removeElements(startVisualLine, startVisualLine - lineDiff)
		}
		refreshRenderData(startVisualLine, endVisualLine)
	}

	private fun checkDirty(): Boolean {
		if (editor.softWrapModel.isDirty) {
			myDirty = true
			return true
		}
		return if (myDirty) {
			myDirty = false
			resetRenderData()
			true
		}else false
	}

	private fun assertValidState() {
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myDirty) return
		if (editor.visibleLineCount != renderDataList.size) {
			LOG.error("Inconsistent state {}", Attachment("glance.txt", editor.dumpState()))
			resetRenderData()
			assert(editor.visibleLineCount == renderDataList.size)
		}
	}

	override fun dispose() {
		super.dispose()
		editor.softWrapModel.applianceManager.removeSoftWrapListener(mySoftWrapChangeListener)
		renderDataList.clear()
		imgArray.forEach { it.flush() }
	}

	private data class LineRenderData(val renderData: Array<RenderData>, val startX: Int, val y: Int, val aboveBlockLine: Int,
									  val lineType: LineType = LineType.CODE, val commentHighlighterEx: RangeHighlighterEx? = null) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as LineRenderData
			if (!renderData.contentEquals(other.renderData)) return false
			if (startX != other.startX) return false
			if (y != other.y) return false
			if (aboveBlockLine != other.aboveBlockLine) return false
			if (lineType != other.lineType) return false
			if (commentHighlighterEx != other.commentHighlighterEx) return false
			return true
		}

		override fun hashCode(): Int {
			var result = renderData.contentHashCode()
			result = 31 * result + startX
			result = 31 * result + y
			result = 31 * result + aboveBlockLine
			result = 31 * result + lineType.hashCode()
			result = 31 * result + (commentHighlighterEx?.hashCode() ?: 0)
			return result
		}
	}

	private data class RenderData(val renderCode: IntArray, val color: Color) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as RenderData
			if (!renderCode.contentEquals(other.renderCode)) return false
			if (color != other.color) return false
			return true
		}

		override fun hashCode(): Int {
			var result = renderCode.contentHashCode()
			result = 31 * result + color.hashCode()
			return result
		}
	}

	private enum class LineType{ CODE, COMMENT, CUSTOM_FOLD}

	@Suppress("UNCHECKED_CAST")
	private companion object{
		private val LOG = LoggerFactory.getLogger(FastMainMinimap::class.java)
		private const val HOOK_METHOD = "onRecalculationEnd"
		private val platformClassLoader = EditorImpl::class.java.classLoader
		private val softWrapListenerClass = arrayOf(Class.forName("com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListener"))
		private val softWrapListeners = SoftWrapApplianceManager::class.java.getDeclaredField("myListeners").apply {
			isAccessible = true
		}

		private fun SoftWrapApplianceManager.addSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).add(listener)
		}

		private fun SoftWrapApplianceManager.removeSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).add(listener)
		}

		private fun CharSequence.toIntArray() = map { it.code }.toIntArray()
	}
}