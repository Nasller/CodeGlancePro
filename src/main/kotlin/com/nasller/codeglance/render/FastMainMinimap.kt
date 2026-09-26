package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.HighlighterListener
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.ui.EdtInvocationManager
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import com.nasller.codeglance.util.Util.isMarkAttributes
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.jetbrains.concurrency.CancellablePromise
import org.slf4j.LoggerFactory
import java.beans.PropertyChangeEvent
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

@Suppress("UnstableApiUsage")
class FastMainMinimap(glancePanel: GlancePanel) : BaseMinimap(glancePanel), HighlighterListener{
	private val myDocument = editor.document
	override val rangeList: MutableList<Pair<Int, Range<Double>>> = ContainerUtil.createLockFreeCopyOnWriteList()
	private val renderDataList = ObjectArrayList<LineRenderData>().also {
		it.addAll(ObjectArrayList.wrap(arrayOfNulls(editor.visibleLineCount)))
	}
	private val mySoftWrapChangeListener = Proxy.newProxyInstance(platformClassLoader, arrayOf(softWrapListenerClass)) { _, method, args ->
		return@newProxyInstance when (method.name) {
			HOOK_ON_REGION_REPARSE_END_METHOD if args?.size == 1 -> onSoftWrapRecalculationEnd(args[0] as IncrementalCacheUpdateEvent)
			HOOK_ON_ALL_DIRTY_REGIONS_REPARSED_METHOD if args == null -> onAllDirtySoftWrapRegionsReparsed()
			HOOK_RESET_METHOD if args == null -> onSoftWrapReset()
			else -> null
		}
	}.also { editor.softWrapModel.addSoftWrapListener(it) }
	private var previewImg = EMPTY_IMG
	private val myRenderDirty = AtomicBoolean(false)
	private val rangeHighlightUpdateAlarm = SingleAlarm.singleAlarm(
		RANGE_HIGHLIGHT_UPDATE_DELAY_MS,
		service<CodeGlanceConfigService>().coroutineScope
	) { flushRangeHighlightUpdates() }
	private val rangeHighlightUpdateLock = Any()
	private var pendingRangeHighlightStartOffset = Int.MAX_VALUE
	private var pendingRangeHighlightEndOffset = Int.MIN_VALUE
	init {
		makeListener()
		editor.addHighlighterListener(this, this)
	}

	override fun getImageOrUpdate() = previewImg

	override fun updateMinimapImage(canUpdate: Boolean){
		if (!canUpdate) return
		if(lock.compareAndSet(false,true)) {
			val action = Runnable {
				ApplicationManager.getApplication().executeOnPooledThread {
					val myScrollState = glancePanel.scrollState.clone()
					try {
						update(renderDataList.toList(), myScrollState)
					}finally {
						invokeLater(modalityState){
							lock.set(false)
							glancePanel.repaint()
							if (myRenderDirty.getAndSet(false) || myScrollState.scale != scrollState.scale ||
									myScrollState.getRenderHeight() != scrollState.getRenderHeight()) {
								updateMinimapImage()
							}
						}
					}
				}
			}
			if(glancePanel.markState.hasMarkHighlight()){
				glancePanel.psiDocumentManager.performForCommittedDocument(myDocument, action)
			}else action.run()
		}else {
			myRenderDirty.set(true)
		}
	}

	override fun rebuildDataAndImage() = runInEdt(modalityState){ if(canUpdate()) resetMinimapData() }

	@Suppress("UndesirableClassUsage")
	private fun update(copyList: List<LineRenderData?>, myScrollState: ScrollState) {
		val pixelsPerLine = myScrollState.pixelsPerLine
		val scale = myScrollState.scale
		val pixScale = getRasterScale()
		val curImg = (if(glancePanel.checkVisible()) {
			if(pixelsPerLine < 1){
				getBufferedImage(myScrollState)
			}else {
				var contentHeight = 5 * pixelsPerLine
				for (lineData in copyList) {
					if(lineData != null) {
						contentHeight += lineData.getLineHeight(pixelsPerLine, scale) + lineData.aboveBlockLine * scale
					}
				}
				val height = max(myScrollState.documentHeight.toDouble(), contentHeight)
				createMinimapImage(
					getRasterWidth(glancePanel.getLogicalWidth(), pixScale),
					getRasterHeight(height, pixScale)
				)
			}
		} else null) ?: return
		val renderHeight = myScrollState.getRenderHeight()
		val graphics = curImg.createGraphics().apply {
			EditorUIUtil.setupAntialiasing(this)
			scale(pixScale, pixScale)
		}
		val docCommentRgb by lazy(LazyThreadSafetyMode.NONE){
			editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor?.rgb
		}
		val defaultRgb = editor.colorsScheme.defaultForeground.rgb
		var totalY = 0.0
		var skipY = 0.0
		var preSetPixelY = -1
		val curRangeList = mutableListOf<Pair<Int, Range<Double>>>()
		for ((index, it) in copyList.withIndex()) {
			if(it == null) continue
			val y = it.getLineHeight(pixelsPerLine, scale)
			val aboveBlockLine = it.aboveBlockLine * scale
			//Coordinates
			if(it.lineType == LineType.CUSTOM_FOLD){
				curRangeList.add(index to Range(totalY, totalY + y - pixelsPerLine + aboveBlockLine))
			}else if(aboveBlockLine > 0){
				curRangeList.add(index - 1 to Range(totalY, totalY + y - pixelsPerLine + aboveBlockLine))
			}
			//Skipping
			if(skipY > 0){
				if(skipY in 0.0 .. aboveBlockLine){
					totalY += aboveBlockLine
					skipY = 0.0
				}else {
					val curY = aboveBlockLine + y
					totalY += curY
					skipY -= curY
					continue
				}
			}else if(aboveBlockLine > 0){
				totalY += aboveBlockLine
			}
			//Rendering
			if(it !== DefaultLineRenderData){
				when(it.lineType){
					null -> if(preSetPixelY != totalY.toInt()){
						var curX = it.startX ?: 0
						val curY = totalY.toInt()
						breakY@ for ((renderChar, rgb) in it.renderData) {
							(rgb ?: defaultRgb).setColorRgb()
							for (char in renderChar) {
								curX += when (char.code) {
									9 -> 4 //TAB
									10 -> break@breakY
									else -> {
										curImg.renderImage(curX, curY, char.code, renderHeight, pixScale)
										1
									}
								}
							}
						}
					}
					LineType.CUSTOM_FOLD -> if(it.customFoldRegion != null){
						//this is render document
						val foldRegion = it.customFoldRegion
						val foldStartOffset = foldRegion.startOffset
						val line = myDocument.getLineNumber(foldStartOffset) - 1 + (y / pixelsPerLine).toInt()
						val foldEndOffset = foldRegion.endOffset.run {
							if(DocumentUtil.isValidLine(line, myDocument)) {
								val lineEndOffset = myDocument.getLineEndOffset(line)
								if(this < lineEndOffset || foldStartOffset > lineEndOffset) this
								else lineEndOffset
							}else this
						}
						var curX = it.startX ?: 0
						var curY = totalY
						(docCommentRgb ?: defaultRgb).setColorRgb()
						for (char in myDocument.getText(TextRange(foldStartOffset, foldEndOffset))) {
							val renderY = curY.toInt()
							when (char.code) {
								9 -> curX += 4 //TAB
								10 -> {//ENTER
									curX = 0
									preSetPixelY = renderY
									curY += pixelsPerLine
								}
								else -> {
									if(preSetPixelY != renderY){
										curImg.renderImage(curX, renderY, char.code, renderHeight, pixScale)
									}
									curX += 1
								}
							}
						}
					}
					LineType.MARK -> {
						val markAttributes = it.commentHighlighterEx!!.getTextAttributes(editor.colorsScheme)
						UISettings.setupAntialiasing(graphics)
						val font = createMarkFont(markAttributes!!)
						graphics.color = markAttributes.errorStripeColor
						val commentText = it.commentHighlighterEx.getUserData(MarkState.BOOK_MARK_DESC_KEY) ?:
						myDocument.getText(TextRange(it.commentHighlighterEx.startOffset, it.commentHighlighterEx.endOffset)).trim()
						val textFont = createMarkTextFont(commentText, font, markAttributes.fontType)
						graphics.font = textFont
						graphics.drawString(commentText, it.startX ?: 0, computeMarkBaseline(totalY, textFont, pixelsPerLine, pixScale))
						skipY = computeMarkOverflowHeight(textFont, pixelsPerLine, pixScale)
					}
				}
			}
			preSetPixelY = totalY.toInt()
			totalY += y
		}
		graphics.dispose()
		if(rangeList.isNotEmpty() || curRangeList.isNotEmpty()){
			rangeList.clear()
			rangeList.addAll(curRangeList)
		}
		previewImg.let {
			previewImg = curImg
			it.flush()
		}
	}

	private fun createHighlighterIterator(text: CharSequence, start: Int, end: Int) = editor.highlighter.run {
		if(this is EmptyEditorHighlighter) OneLineHighlightDelegate(text, start, end)
		else{
			val highlighterIterator = createIterator(start)
			if(isLogFile){
				if(highlighterIterator::class.java.name.contains("EmptyEditorHighlighter")){
					OneLineHighlightDelegate(text, start, end)
				}else IdeLogFileHighlightDelegate(myDocument, highlighterIterator)
			}else highlighterIterator
		}
	}

	private fun updateMinimapData(visLinesIterator: MyVisualLinesIterator, endVisualLine: Int?){
		val text = myDocument.immutableCharSequence
		val markCommentMap = glancePanel.markState.getAllMarkHighlight()
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, myDocument) }
		val limitWidth = glancePanel.getLogicalWidth()
		while (!visLinesIterator.atEnd()) {
			checkCanceled()
			val start = visLinesIterator.getVisualLineStartOffset()
			val end = visLinesIterator.getVisualLineEndOffset()
			val visualLine = visLinesIterator.getVisualLine()
			//Check invalid somethings in background task
			if(visualLine >= renderDataList.size || start > end) return
			//BLOCK_INLAY
			val aboveBlockLine = visLinesIterator.getBlockInlaysAbove().sumOf { it.heightInPixels }
			//CUSTOM_FOLD
			var foldRegion = visLinesIterator.getCurrentFoldRegion()
			var foldStartOffset = foldRegion?.startOffset ?: -1
			if(foldRegion is CustomFoldRegion && foldStartOffset == start){
				renderDataList[visualLine] = LineRenderData(emptyArray(), null, aboveBlockLine,
						LineType.CUSTOM_FOLD, customFoldRegion = foldRegion)
			}else {
				//COMMENT
				if(markCommentMap.containsKey(start)) {
					renderDataList[visualLine] = LineRenderData(emptyArray(), 2, aboveBlockLine,
						LineType.MARK, commentHighlighterEx = markCommentMap[start])
				}else if(start < text.length && start < end){
					var hlIter = createHighlighterIterator(text, start, end)
					if(hlIter is OneLineHighlightDelegate || !hlIter.atEnd()){
						val renderList = mutableListOf<RenderData>()
						val resolvedHighlightRanges = resolveHighlightRanges(start, end, getHighlightColor(start, end))
						var highlightRangeIndex = 0
						var foldLineIndex = visLinesIterator.getStartFoldingIndex()
						var width = 0
						var lineHasVisibleChars = false
						do {
							checkCanceled()
							var curStart = hlIter.start.run{ if(start > this) start else this }
							val curEnd = hlIter.end.run{ if(this - curStart > limitWidth) start + limitWidth else this }
							if(width > limitWidth || curEnd > text.length || curStart > curEnd) break
							//FOLD
							if(curStart == foldStartOffset){
								val foldEndOffset = foldRegion!!.endOffset
								val foldText = StringUtil.replace(foldRegion.placeholderText, "\n", " ").toCharArray()
								width += foldText.size
								lineHasVisibleChars = lineHasVisibleChars || foldText.any { !it.isWhitespace() }
								renderList.add(RenderData(foldText, editor.foldingModel.placeholderAttributes?.foregroundColor?.rgb))
								foldRegion = visLinesIterator.getFoldRegion(++foldLineIndex)
								foldStartOffset = foldRegion?.startOffset ?: -1
								//case on fold InLine
								if(foldEndOffset < curEnd){
									curStart = foldEndOffset
								}else {
									//A fold (e.g. collapsed unchanged diff fragment) may span thousands of lines: seek past it instead of walking its tokens
									if(hlIter !is OneLineHighlightDelegate) hlIter = createHighlighterIterator(text, foldEndOffset, end)
									while (!hlIter.atEnd() && hlIter.start < foldEndOffset) hlIter.advance()
									continue
								}
							}
							//CODE
							val renderStr = CharArray(curEnd - curStart)
							var segmentHasVisibleChars = false
							for (index in renderStr.indices) {
								val char = text[curStart + index]
								renderStr[index] = char
								if (!segmentHasVisibleChars && !char.isWhitespace()) segmentHasVisibleChars = true
							}
							width += renderStr.size
							lineHasVisibleChars = lineHasVisibleChars || segmentHasVisibleChars
							if(!segmentHasVisibleChars) {
								renderList.add(RenderData(renderStr))
							}else{
								while (highlightRangeIndex < resolvedHighlightRanges.size &&
									resolvedHighlightRanges[highlightRangeIndex].endOffset <= curStart) {
									highlightRangeIndex++
								}
								val hasHighlight = highlightRangeIndex < resolvedHighlightRanges.size &&
									resolvedHighlightRanges[highlightRangeIndex].startOffset < curEnd
								if(!hasHighlight) {
									renderList.add(RenderData(renderStr.firstLine(),
										runCatching { hlIter.textAttributes.foregroundColor?.rgb }.getOrNull()))
								}else {
									val lexerRgb = runCatching { hlIter.textAttributes.foregroundColor?.rgb }.getOrNull()
										?: editor.colorsScheme.defaultForeground.rgb
									var nextOffset = curStart
									var currentHighlightIndex = highlightRangeIndex
									while (currentHighlightIndex < resolvedHighlightRanges.size) {
										val highlightRange = resolvedHighlightRanges[currentHighlightIndex]
										if(highlightRange.startOffset >= curEnd) break
										val highlightStart = max(nextOffset, highlightRange.startOffset)
										if(nextOffset < highlightStart) {
											renderList.add(RenderData(CharArrayUtil.fromSequence(text, nextOffset, highlightStart), lexerRgb))
										}
										val highlightEnd = min(curEnd, highlightRange.endOffset)
										if(highlightStart < highlightEnd) {
											val highlightChars = if(highlightStart == curStart && highlightEnd == curEnd) {
												renderStr.firstLine()
											}else CharArrayUtil.fromSequence(text, highlightStart, highlightEnd)
											renderList.add(RenderData(highlightChars, highlightRange.rgb))
											nextOffset = highlightEnd
										}
										if(highlightRange.endOffset > curEnd) break
										currentHighlightIndex++
									}
									if(nextOffset < curEnd) {
										renderList.add(RenderData(CharArrayUtil.fromSequence(text, nextOffset, curEnd), lexerRgb))
									}
								}
							}
							hlIter.advance()
						}while (!hlIter.atEnd() && hlIter.start < end)
						renderDataList[visualLine] = if (lineHasVisibleChars) { LineRenderData(renderList.mergeSameRgbCharArray(),
								visLinesIterator.getStartsWithSoftWrap()?.indentInColumns, aboveBlockLine)
						} else DefaultLineRenderData
					}else {
						renderDataList[visualLine] = DefaultLineRenderData
					}
				}else {
					renderDataList[visualLine] = DefaultLineRenderData
				}
			}
			if(endVisualLine == null || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateMinimapImage()
	}

	private fun MutableList<RenderData>.mergeSameRgbCharArray(): Array<RenderData> = when (size){
		0 -> emptyArray()
		1 -> arrayOf(first())
		else -> {
			val mergedData = ArrayList<RenderData>(size)
			var groupStart = 0
			var groupLength = first().renderChar.size
			var groupRgb = first().rgb
			var groupCanMerge = first().renderChar.all { it.isWhitespace() }
			for (index in 1 until size) {
				val data = get(index)
				val dataCanMerge = data.renderChar.all { it.isWhitespace() }
				if(groupCanMerge || dataCanMerge || groupRgb == data.rgb){
					groupLength += data.renderChar.size
					groupRgb = groupRgb ?: data.rgb
					groupCanMerge = groupCanMerge && dataCanMerge
				}else {
					mergedData.add(mergeRenderData(groupStart, index, groupLength, groupRgb))
					groupStart = index
					groupLength = data.renderChar.size
					groupRgb = data.rgb
					groupCanMerge = false
				}
			}
			mergedData.add(mergeRenderData(groupStart, size, groupLength, groupRgb))
			mergedData.toTypedArray()
		}
	}

	private fun MutableList<RenderData>.mergeRenderData(startIndex: Int, endIndex: Int, mergedLength: Int, rgb: Int?): RenderData {
		if (endIndex - startIndex == 1) return get(startIndex)
		val mergedChars = CharArray(mergedLength)
		var destinationOffset = 0
		for (index in startIndex until endIndex) {
			val chars = get(index).renderChar
			chars.copyInto(mergedChars, destinationOffset)
			destinationOffset += chars.size
		}
		return RenderData(mergedChars, rgb)
	}

	private fun resolveHighlightRanges(startOffset: Int, endOffset: Int, highlights: List<RangeHighlightColor>): List<ResolvedHighlightRange> {
		if (startOffset >= endOffset || highlights.isEmpty()) return emptyList()
		val events = ArrayList<HighlightEvent>(highlights.size * 2)
		for ((priority, highlight) in highlights.withIndex()) {
			val start = highlight.startOffset.coerceIn(startOffset, endOffset)
			val end = highlight.endOffset.coerceIn(startOffset, endOffset)
			if(start < end) {
				events.add(HighlightEvent(start, priority, true))
				events.add(HighlightEvent(end, priority, false))
			}
		}
		if(events.isEmpty()) return emptyList()
		events.sortBy { it.offset }
		val activePriorities = TreeSet<Int>()
		val resolvedRanges = mutableListOf<ResolvedHighlightRange>()
		var previousOffset = startOffset
		var eventIndex = 0
		while (eventIndex < events.size) {
			val offset = events[eventIndex].offset
			if(previousOffset < offset && activePriorities.isNotEmpty()) {
				resolvedRanges.addOrMerge(previousOffset, offset, highlights[activePriorities.first()].foregroundColor.rgb)
			}
			while (eventIndex < events.size && events[eventIndex].offset == offset) {
				val event = events[eventIndex]
				if(event.activate) activePriorities.add(event.priority)
				else activePriorities.remove(event.priority)
				eventIndex++
			}
			previousOffset = offset
		}
		if(previousOffset < endOffset && activePriorities.isNotEmpty()) {
			resolvedRanges.addOrMerge(previousOffset, endOffset, highlights[activePriorities.first()].foregroundColor.rgb)
		}
		return resolvedRanges
	}

	private fun MutableList<ResolvedHighlightRange>.addOrMerge(startOffset: Int, endOffset: Int, rgb: Int) {
		val lastRange = lastOrNull()
		if(lastRange != null && lastRange.endOffset == startOffset && lastRange.rgb == rgb) {
			lastRange.endOffset = endOffset
		}else add(ResolvedHighlightRange(startOffset, endOffset, rgb))
	}

	private fun checkCanceled(){
		if(myResetDataPromise != null){
			ProgressManager.checkCanceled()
		}
	}

	private fun resetMinimapData(){
		assert(!myDocument.isInBulkUpdate)
		assert(!editor.inlayModel.isInBatchMode)
		doInvalidateRange(0, myDocument.textLength, true)
	}

	@Volatile
	private var myResetDataPromise: CancellablePromise<Unit>? = null
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
		myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, region.startOffset)
		myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, region.endOffset)
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED == 0 || myDocument.isInBulkUpdate || checkDirty()) return
		val startOffset = region.startOffset
		if (editor.foldingModel.getCollapsedRegionAtOffset(startOffset) !== region) return
		doInvalidateRange(startOffset, startOffset)
	}

	override fun onFoldProcessingStart() {
		if (myDocument.isInBulkUpdate) return
		myFoldingBatchStart = true
	}

	override fun onFoldProcessingEnd() {
		if (myDocument.isInBulkUpdate) return
		if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset && (!myFoldingBatchStart ||
					editor.visibleLineCount != renderDataList.size)) {
			doInvalidateRange(myFoldingChangeStartOffset, myFoldingChangeEndOffset)
		}
		myFoldingBatchStart = false
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
			if(startOffset == 0 && endOffset == myDocument.textLength) {
				if(glancePanel.hideScrollBarListener.isNotRunning().not()) return
				doInvalidateRange(startOffset, endOffset, true)
			}else doInvalidateRange(startOffset, endOffset)
		}
	}

	private fun onAllDirtySoftWrapRegionsReparsed() {
		if (myDocument.isInBulkUpdate) return
		if (myDirty) {
			myDirty = false
			resetMinimapData()
		}
	}

	private fun onSoftWrapReset() {
		myDirty = true
	}

	/** MarkupModelListener & BookmarksListener */
	override fun updateRangeHighlight(highlighter: RangeMarker) {
		EdtInvocationManager.invokeLaterIfNeeded {
			if (!glancePanel.checkVisible() || myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myDuringDocumentUpdate) {
				return@invokeLaterIfNeeded
			}
			when(highlighter){
				is MarkState.BookmarkHighlightDelegate -> queueRangeHighlightUpdate(highlighter.startOffset, highlighter.endOffset)
				is RangeHighlighterEx -> {
					if(highlighter.isThinErrorStripeMark.not() && (highlighter.textAttributesKey?.isMarkAttributes() == true ||
								EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))){
						queueRangeHighlightUpdate(highlighter.affectedAreaStartOffset, highlighter.affectedAreaEndOffset)
					}else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
						glancePanel.repaint()
					}
				}
			}
		}
	}

	private fun queueRangeHighlightUpdate(startOffset: Int, endOffset: Int) {
		val textLength = myDocument.textLength
		val start = MathUtil.clamp(startOffset, 0, textLength)
		val end = MathUtil.clamp(endOffset, start, textLength)
		if (start == end) return
		synchronized(rangeHighlightUpdateLock) {
			pendingRangeHighlightStartOffset = min(pendingRangeHighlightStartOffset, start)
			pendingRangeHighlightEndOffset = max(pendingRangeHighlightEndOffset, end)
		}
		rangeHighlightUpdateAlarm.cancelAndRequest()
	}

	private fun flushRangeHighlightUpdates() {
		if (!ApplicationManager.getApplication().isDispatchThread) {
			invokeLater(modalityState) { flushRangeHighlightUpdates() }
			return
		}
		val range = synchronized(rangeHighlightUpdateLock) {
			if (pendingRangeHighlightStartOffset > pendingRangeHighlightEndOffset) return
			val result = pendingRangeHighlightStartOffset to pendingRangeHighlightEndOffset
			pendingRangeHighlightStartOffset = Int.MAX_VALUE
			pendingRangeHighlightEndOffset = Int.MIN_VALUE
			result
		}
		if (!glancePanel.checkVisible() || myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myDuringDocumentUpdate) return
		val startVisualLine = editor.offsetToVisualLine(range.first, false)
		val endVisualLine = editor.offsetToVisualLine(range.second, true)
		if (endVisualLine - startVisualLine > MAX_SYNCHRONOUS_HIGHLIGHT_LINES) {
			resetMinimapData()
		}else {
			invalidateRange(range.first, range.second)
		}
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName) return
		resetMinimapData()
	}

	/** HighlighterListener */
	override fun highlighterChanged(startOffset: Int, endOffset: Int) {
		queueRangeHighlightUpdate(startOffset, endOffset)
	}

	private fun invalidateRange(startOffset: Int, endOffset: Int) {
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode ||
				startOffset > endOffset || startOffset >= myDocument.textLength || endOffset < 0) return
		if (myDuringDocumentUpdate) {
			myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, startOffset)
			myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, endOffset)
		} else if (myFoldingChangeEndOffset != Int.MIN_VALUE) {
			myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, startOffset)
			myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, endOffset)
		} else {
			doInvalidateRange(startOffset, endOffset)
		}
	}

	private fun doInvalidateRange(startOffset: Int, endOffset: Int, reset: Boolean = false) {
		if (checkOutOfLineRange {
				renderDataList.clear()
				previewImg = EMPTY_IMG
				invokeLater { glancePanel.repaint() }
		} || checkDirty() || checkProcessReset(startOffset,endOffset,reset)) return
		val startVisualLine = editor.offsetToVisualLine(startOffset, false)
		val endVisualLine = editor.offsetToVisualLine(endOffset, true)
		val visibleLineCount = editor.visibleLineCount
		glancePanel.lineCount = visibleLineCount
		val lineDiff = visibleLineCount - renderDataList.size
		try {
			if (lineDiff > 0) {
				renderDataList.addAll(startVisualLine, ObjectArrayList.wrap(arrayOfNulls(lineDiff)))
			}else if (lineDiff < 0) {
				renderDataList.removeElements(startVisualLine, startVisualLine - lineDiff)
			}
		}catch (e: IndexOutOfBoundsException) {
			LOG.error("File: ${virtualFile?.name} FileType: ${virtualFile?.fileType?.name ?: "Unknown"} " +
					"RenderDataList.Size: ${renderDataList.size} VisibleLineCount: $visibleLineCount " +
					"startVisualLine: $startVisualLine endVisualLine: $endVisualLine " +
					"Text: ${editor.document.getText(TextRange(startOffset, endOffset))}", e)
			invokeLater { rebuildDataAndImage() }
			return
		}
		submitUpdateMinimapDataTask(startVisualLine, endVisualLine, reset)
	}

	private fun submitUpdateMinimapDataTask(startVisualLine: Int, endVisualLine: Int, reset: Boolean) {
		if(!glancePanel.checkVisible()) return
		try {
			val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
			if(reset) {
				myResetDataPromise = ReadAction.nonBlocking<Unit> {
//					val startTime = System.currentTimeMillis()
					updateMinimapData(visLinesIterator, null)
//					println("updateMinimapData time: ${System.currentTimeMillis() - startTime}")
				}.withDocumentsCommitted(glancePanel.project).coalesceBy(this).expireWith(this)
					.finishOnUiThread(ModalityState.any()) {
						myResetDataPromise = null
						if (myResetChangeStartOffset <= myResetChangeEndOffset) {
							doInvalidateRange(myResetChangeStartOffset, myResetChangeEndOffset)
							myResetChangeStartOffset = Int.MAX_VALUE
							myResetChangeEndOffset = Int.MIN_VALUE
							assertValidState()
						}
					}.submit(fastMinimapBackendExecutor).onError {
						myResetDataPromise = null
						if (it !is CancellationException) {
							LOG.warn("Async update error fileType:${virtualFile?.fileType?.name}", it)
							invokeLater { resetMinimapData() }
						}
					}
			}else {
//				val startTime = System.currentTimeMillis()
				updateMinimapData(visLinesIterator, endVisualLine)
//				println("updateMinimapData time: ${System.currentTimeMillis() - startTime}")
			}
		}catch (e: Throwable){
			LOG.error("updateMinimapData error fileType:${virtualFile?.fileType?.name}", e)
		}
	}

	//check has background tasks
	private fun checkProcessReset(startOffset: Int, endOffset: Int, reset: Boolean): Boolean{
		if (myResetDataPromise != null) {
			if(myResetDataPromise?.isDone == false){
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
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myResetDataPromise != null || myDirty || outOfLineRange) return
		if (editor.visibleLineCount != renderDataList.size) {
			LOG.error("Inconsistent state {}", Attachment("glance.txt", editor.dumpState()))
			resetMinimapData()
			assert(editor.visibleLineCount == renderDataList.size)
		}
	}

	override fun dispose() {
		rangeHighlightUpdateAlarm.cancel()
		synchronized(rangeHighlightUpdateLock) {
			pendingRangeHighlightStartOffset = Int.MAX_VALUE
			pendingRangeHighlightEndOffset = Int.MIN_VALUE
		}
		rangeList.clear()
		editor.softWrapModel.removeSoftWrapListener(mySoftWrapChangeListener)
		previewImg.flush()
	}

	private data class LineRenderData(val renderData: Array<RenderData>, val startX: Int?,
									  val aboveBlockLine: Int,
									  val lineType: LineType? = null,
									  val customFoldRegion: CustomFoldRegion? = null,
									  val commentHighlighterEx: RangeHighlighterEx? = null) {
		fun getLineHeight(pixelsPerLine: Double, scale: Double) = if(lineType == LineType.CUSTOM_FOLD && customFoldRegion != null) {
			(customFoldRegion.heightInPixels * scale).run{
				if(this < pixelsPerLine) pixelsPerLine else this
			}
		} else pixelsPerLine

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as LineRenderData
			if (!renderData.contentEquals(other.renderData)) return false
			if (startX != other.startX) return false
			if (aboveBlockLine != other.aboveBlockLine) return false
			if (lineType != other.lineType) return false
			if (customFoldRegion != other.customFoldRegion) return false
			if (commentHighlighterEx != other.commentHighlighterEx) return false
			return true
		}

		override fun hashCode(): Int {
			var result = renderData.contentHashCode()
			result = 31 * result + (startX ?: 0)
			result = 31 * result + aboveBlockLine
			result = 31 * result + lineType.hashCode()
			result = 31 * result + customFoldRegion.hashCode()
			result = 31 * result + commentHighlighterEx.hashCode()
			return result
		}
	}

	private data class RenderData(val renderChar: CharArray, val rgb: Int? = null){
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false
			other as RenderData
			if (!renderChar.contentEquals(other.renderChar)) return false
			if (rgb != other.rgb) return false
			return true
		}

		override fun hashCode(): Int {
			var result = renderChar.contentHashCode()
			result = 31 * result + (rgb ?: 0)
			return result
		}
	}

	private data class HighlightEvent(val offset: Int, val priority: Int, val activate: Boolean)

	private data class ResolvedHighlightRange(val startOffset: Int, var endOffset: Int, val rgb: Int)

	private enum class LineType{MARK, CUSTOM_FOLD}

	@Suppress("UNCHECKED_CAST")
	companion object{
		private val LOG = LoggerFactory.getLogger(FastMainMinimap::class.java)
		private const val RANGE_HIGHLIGHT_UPDATE_DELAY_MS = 50
		private const val MAX_SYNCHRONOUS_HIGHLIGHT_LINES = 256
		private const val HOOK_ON_REGION_REPARSE_END_METHOD = "onRegionReparseEnd"
		private const val HOOK_ON_ALL_DIRTY_REGIONS_REPARSED_METHOD = "onAllDirtyRegionsReparsed"
		private const val HOOK_RESET_METHOD = "reset"
		private val platformClassLoader = EditorImpl::class.java.classLoader
		private val softWrapListenerClass = Class.forName("com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener")
		private val addSoftWrapParsingListener = SoftWrapModelImpl::class.java.getDeclaredMethod("addSoftWrapParsingListener", softWrapListenerClass).apply {
			isAccessible = true
		}
		private val removeSoftWrapParsingListener = SoftWrapModelImpl::class.java.getDeclaredMethod("removeSoftWrapParsingListener", softWrapListenerClass).apply {
			isAccessible = true
		}
		private val DefaultLineRenderData = LineRenderData(emptyArray(), null, 0)
		private val fastMinimapBackendExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FastMinimapBackendExecutor", 1)

		private fun SoftWrapModelImpl.addSoftWrapListener(listener: Any) {
			addSoftWrapParsingListener.invoke(this, listener)
		}

		private fun SoftWrapModelImpl.removeSoftWrapListener(listener: Any) {
			removeSoftWrapParsingListener.invoke(this, listener)
		}

		private fun CharArray.firstLine(): CharArray{
			val index = indexOf('\n')
			return if(index > 0) copyOfRange(0, index) else this
		}
	}
}