package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.config.CodeGlanceColorsPage
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
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
	override val rangeList: MutableList<Pair<Int, Range<Int>>> = ContainerUtil.createLockFreeCopyOnWriteList()
	private val renderDataList = ObjectArrayList<LineRenderData>().also {
		it.addAll(ObjectArrayList.wrap(arrayOfNulls(editor.visibleLineCount)))
	}
	private val mySoftWrapChangeListener = Proxy.newProxyInstance(platformClassLoader, softWrapListenerClass) { _, method, args ->
		return@newProxyInstance if(HOOK_ON_RECALCULATION_END_METHOD == method.name && args?.size == 1){
			 onSoftWrapRecalculationEnd(args[0] as IncrementalCacheUpdateEvent)
		}else null
	}.also { editor.softWrapModel.applianceManager.addSoftWrapListener(it) }
	private val imgArray = arrayOf(getBufferedImage(), getBufferedImage())
	@Volatile
	private var myLastImageIndex = 0
	@Volatile
	private var myResetDataPromise: CancellablePromise<Unit>? = null
	private var myRenderDirty = false
	init { makeListener() }

	override fun getImageOrUpdate(): BufferedImage {
		return imgArray[myLastImageIndex]
	}

	override fun updateMinimapImage(canUpdate: Boolean){
		if (canUpdate && myDocument.textLength > 0) {
			if(lock.compareAndSet(false,true)) {
				val copyList = renderDataList.toList()
				glancePanel.psiDocumentManager.performForCommittedDocument(editor.document) {
					ReadAction.nonBlocking<Unit>{
						update(copyList)
					}.expireWith(this).finishOnUiThread(ModalityState.any()) {
						lock.set(false)
						glancePanel.repaint()
						if (myRenderDirty) {
							myRenderDirty = false
							updateMinimapImage()
						}
					}.submit(AppExecutorUtil.getAppExecutorService())
				}
			}else {
				myRenderDirty = true
			}
		}
	}

	override fun rebuildDataAndImage() {
		if(canUpdate()) {
			ReadAction.compute<Unit,Throwable>{ resetMinimapData() }
		}
	}

	private fun getMinimapImage(drawHeight: Int): BufferedImage? {
		val index = if(myLastImageIndex == 0) 1 else 0
		var curImg = imgArray[index]
		if (curImg.height < drawHeight || curImg.width < glancePanel.width) {
			curImg.flush()
			curImg = getBufferedImage(drawHeight)
			imgArray[index] = curImg
		}
		return if(glancePanel.checkVisible()) curImg else null
	}

	private fun update(copyList: List<LineRenderData?>){
		val curImg = getMinimapImage(copyList.filterNotNull().sumOf { it.y + it.aboveBlockLine }) ?: return
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
		for ((index, it) in copyList.withIndex()) {
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
					breakY@ for (renderData in it.renderData) {
						renderData.color.setColorRgba()
						for (char in renderData.renderChar) {
							curImg.renderImage(curX, curY, char.code)
							when (char.code) {
								9 -> curX += 4 //TAB
								10 -> {//ENTER
									if(it.lineType == LineType.CUSTOM_FOLD){
										curX = 0
										curY += config.pixelsPerLine
									}else break@breakY
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
		if(rangeList.isNotEmpty() || curRangeList.isNotEmpty()){
			rangeList.clear()
			rangeList.addAll(curRangeList)
		}
		myLastImageIndex = if(myLastImageIndex == 0) 1 else 0
	}

	private fun submitUpdateMinimapDataTask(startVisualLine: Int, endVisualLine: Int, reset: Boolean) {
		if(!glancePanel.checkVisible()) return
		try {
			val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
			if(reset){
				myResetDataPromise = ReadAction.nonBlocking<Unit> {
					updateMinimapData(visLinesIterator, 0)
				}.expireWith(this).finishOnUiThread(ModalityState.any()) {
					if (myResetChangeStartOffset <= myResetChangeEndOffset) {
						doInvalidateRange(myResetChangeStartOffset, myResetChangeEndOffset)
						myResetChangeStartOffset = Int.MAX_VALUE
						myResetChangeEndOffset = Int.MIN_VALUE
						assertValidState()
					}
				}.submit(AppExecutorUtil.getAppExecutorService()).onSuccess {
					myResetDataPromise = null
				}.onError {
					myResetDataPromise = null
				}
			}else updateMinimapData(visLinesIterator, endVisualLine)
		}catch (e: Throwable){
			LOG.error("submitMinimapDataUpdateTask error",e)
		}
	}

	private fun updateMinimapData(visLinesIterator: MyVisualLinesIterator, endVisualLine: Int){
		val text = myDocument.immutableCharSequence
		val defaultColor = editor.colorsScheme.defaultForeground
		val docComment by lazy(LazyThreadSafetyMode.NONE){
			editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor
		}
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, myDocument) }
		val limitWidth = glancePanel.getConfigSize().width
		while (!visLinesIterator.atEnd()) {
			val start = visLinesIterator.getVisualLineStartOffset()
			val visualLine = visLinesIterator.getVisualLine()
			if(myResetDataPromise != null) {
				//Check invalid line in background task
				if(myResetDataPromise!!.isCancelled || visualLine >= renderDataList.size) return
			}
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
				renderDataList[visualLine] = LineRenderData(listOf(RenderData(text.subSequence(foldStartOffset,
					if(DocumentUtil.isValidLine(line, myDocument)) {
						val lineEndOffset = myDocument.getLineEndOffset(line)
						if(foldEndOffset < lineEndOffset) foldEndOffset else lineEndOffset
					}else foldEndOffset
				), docComment ?: defaultColor)), 0, heightLine, aboveBlockLine, LineType.CUSTOM_FOLD)
			}else {
				//COMMENT
				if(markCommentMap.containsKey(start)) {
					renderDataList[visualLine] = LineRenderData(emptyList(), 2, config.pixelsPerLine, aboveBlockLine,
						LineType.COMMENT, commentHighlighterEx = markCommentMap[start])
				}else {
					val renderList = mutableListOf<RenderData>()
					if(start < text.length){
						val end = visLinesIterator.getVisualLineEndOffset()
						var foldLineIndex = visLinesIterator.getStartFoldingIndex()
						val hlIter = editor.highlighter.run {
							if(this is EmptyEditorHighlighter) OneLineHighlightDelegate(myDocument, start, end)
							else{
								val highlighterIterator = createIterator(start)
								if(isLogFile){
									if(highlighterIterator::class.java.name.contains("EmptyEditorHighlighter")){
										OneLineHighlightDelegate(myDocument, start, end)
									}else IdeLogFileHighlightDelegate(myDocument, highlighterIterator)
								}else highlighterIterator
							}
						}
						if(hlIter is OneLineHighlightDelegate || !hlIter.atEnd()){
							var width = 0
							do {
								val curEnd = hlIter.end
								if(width > limitWidth || curEnd >= text.length) break
								var curStart = if(start > hlIter.start && start < curEnd) start else hlIter.start
								//FOLD
								if(curStart == foldStartOffset){
									val foldEndOffset = foldRegion!!.endOffset
									val foldText = StringUtil.replace(foldRegion.placeholderText, "\n", " ")
									width += foldText.length
									renderList.add(RenderData(foldText, editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor))
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
								val renderStr = text.subSequence(curStart, limitLength(curStart,curEnd,limitWidth))
								width += renderStr.length
								if(renderStr.isBlank()) {
									renderList.add(RenderData(renderStr, defaultColor))
								}else{
									val highlightList = getHighlightColor(curStart, curEnd)
									if(highlightList.isNotEmpty()){
										if(highlightList.size == 1 && highlightList.first().run{ startOffset == curStart && endOffset == curEnd }){
											renderList.add(RenderData(renderStr, highlightList.first().foregroundColor))
										}else {
											val lexerColor = runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() ?: defaultColor
											var nextOffset = curStart
											var preColor: Color? = null
											for(offset in curStart .. curEnd){
												val color = highlightList.firstOrNull {
													offset >= it.startOffset && offset < it.endOffset
												}?.foregroundColor ?: lexerColor
												if(preColor != null && preColor !== color){
													renderList.add(RenderData(text.subSequence(nextOffset,
														limitLength(nextOffset,offset,limitWidth)), preColor))
													nextOffset = offset
												}
												preColor = color
											}
											if(nextOffset < curEnd){
												renderList.add(RenderData(text.subSequence(nextOffset,
													limitLength(nextOffset,curEnd,limitWidth)), preColor ?: lexerColor))
											}
										}
									}else {
										renderList.add(RenderData(renderStr, runCatching {
											hlIter.textAttributes.foregroundColor
										}.getOrNull() ?: defaultColor))
									}
								}
								hlIter.advance()
							}while (!hlIter.atEnd() && hlIter.start < end)
						}
					}
					renderDataList[visualLine] = LineRenderData(renderList,
						visLinesIterator.getStartsWithSoftWrap()?.indentInColumns ?: 0, config.pixelsPerLine, aboveBlockLine)
				}
			}
			if(endVisualLine == 0 || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateMinimapImage()
	}

	private fun resetMinimapData(){
		assert(!myDocument.isInBulkUpdate)
		assert(!editor.inlayModel.isInBatchMode)
		doInvalidateRange(0, myDocument.textLength, true)
	}

	private var myDirty = false
	private var myFoldingBatchStart = false
	private var myFoldingChangeStartOffset = Int.MAX_VALUE
	private var myFoldingChangeEndOffset = Int.MIN_VALUE
	private var myDuringDocumentUpdate = false
	private var myDocumentChangeStartOffset = 0
	private var myDocumentChangeEndOffset = 0
	private var myResetChangeStartOffset = Int.MAX_VALUE
	private var myResetChangeEndOffset = Int.MIN_VALUE
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
		submitUpdateMinimapDataTask(visualLine, visualLine, false)
	}

	override fun onFoldProcessingStart() {
		myFoldingBatchStart = true
	}

	override fun onFoldProcessingEnd() {
		if (myDocument.isInBulkUpdate) return
		if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset && (!myFoldingBatchStart ||
					editor.visibleLineCount != renderDataList.size)) {
			doInvalidateRange(myFoldingChangeStartOffset, myFoldingChangeEndOffset)
		}
		myFoldingChangeStartOffset = Int.MAX_VALUE
		myFoldingChangeEndOffset = Int.MIN_VALUE
		myFoldingBatchStart = false
		assertValidState()
	}

	/** InlayModel.SimpleAdapter */
	override fun onAdded(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onRemoved(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) = checkinInlayAndUpdate(inlay, changeFlags)

	private fun checkinInlayAndUpdate(inlay: Inlay<*>, changeFlags: Int? = null) {
		if(myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Inlay.Placement.ABOVE_LINE
			|| (changeFlags != null && changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0) || myDuringDocumentUpdate) return
		val offset = inlay.offset
		doInvalidateRange(offset,offset)
	}

	override fun onBatchModeFinish(editor: Editor) {
		if (myDocument.isInBulkUpdate) return
		resetMinimapData()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			resetMinimapData()
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
			val startOffset = event.startOffset
			val endOffset = event.actualEndOffset
			doInvalidateRange(startOffset, endOffset, startOffset == 0 && endOffset == myDocument.textLength)
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
		resetMinimapData()
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

	private fun doInvalidateRange(startOffset: Int, endOffset: Int, reset: Boolean = false) {
		if (checkDirty() || checkProcessReset(startOffset,endOffset,reset)) return
		val startVisualLine = editor.offsetToVisualLine(startOffset, false)
		val endVisualLine = editor.offsetToVisualLine(endOffset, true)
		val lineDiff = editor.visibleLineCount - renderDataList.size
		if (lineDiff > 0) {
			renderDataList.addAll(startVisualLine, ObjectArrayList.wrap(arrayOfNulls(lineDiff)))
		}else if (lineDiff < 0) {
			renderDataList.removeElements(startVisualLine, startVisualLine - lineDiff)
		}
		submitUpdateMinimapDataTask(startVisualLine, endVisualLine, reset)
	}

	//check has background tasks
	private fun checkProcessReset(startOffset: Int, endOffset: Int,reset: Boolean): Boolean{
		if (myResetDataPromise != null) {
			if(myResetDataPromise?.state == Promise.State.PENDING){
				if(reset) {
					myResetDataPromise?.cancel()
					myResetChangeStartOffset = Int.MAX_VALUE
					myResetChangeEndOffset = Int.MIN_VALUE
				}else {
					myResetChangeStartOffset = min(myResetChangeStartOffset, startOffset)
					myResetChangeEndOffset = max(myResetChangeEndOffset, endOffset)
					return true
				}
			}
			myResetDataPromise = null
		}
		return false
	}

	private fun checkDirty(): Boolean {
		if (editor.softWrapModel.isDirty) {
			myDirty = true
			return true
		}
		return if (myDirty) {
			myDirty = false
			resetMinimapData()
			true
		}else false
	}

	private fun assertValidState() {
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myResetDataPromise != null || myDirty) return
		if (editor.visibleLineCount != renderDataList.size) {
			LOG.error("Inconsistent state {}", Attachment("glance.txt", editor.dumpState()))
			resetMinimapData()
			assert(editor.visibleLineCount == renderDataList.size)
		}
	}

	override fun dispose() {
		super.dispose()
		editor.softWrapModel.applianceManager.removeSoftWrapListener(mySoftWrapChangeListener)
		renderDataList.clear()
		imgArray.forEach { it.flush() }
	}

	private data class LineRenderData(val renderData: List<RenderData>, val startX: Int, val y: Int, val aboveBlockLine: Int,
									  val lineType: LineType = LineType.CODE, val commentHighlighterEx: RangeHighlighterEx? = null)

	private data class RenderData(val renderChar: CharSequence, val color: Color)

	private enum class LineType{ CODE, COMMENT, CUSTOM_FOLD}

	@Suppress("UNCHECKED_CAST")
	private companion object{
		private val LOG = LoggerFactory.getLogger(FastMainMinimap::class.java)
		private const val HOOK_ON_RECALCULATION_END_METHOD = "onRecalculationEnd"
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

		private fun limitLength(start: Int, end: Int, limit: Int): Int{
			val length = end - start
			return if(length > limit) start + limit else end
		}
	}
}