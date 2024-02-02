package com.nasller.codeglance.render

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.*
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
import com.intellij.openapi.editor.impl.HighlighterListener
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.DocumentEventUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.MathUtil
import com.intellij.util.Range
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MyVisualLinesIterator
import com.nasller.codeglance.util.Util
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.jetbrains.concurrency.CancellablePromise
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class FastMainMinimap(glancePanel: GlancePanel) : BaseMinimap(glancePanel), HighlighterListener{
	private val myDocument = editor.document
	override val rangeList: MutableList<Pair<Int, Range<Double>>> = ContainerUtil.createLockFreeCopyOnWriteList()
	private val renderDataList = ObjectArrayList<LineRenderData>().also {
		it.addAll(ObjectArrayList.wrap(arrayOfNulls(editor.visibleLineCount)))
	}
	private val mySoftWrapChangeListener = Proxy.newProxyInstance(platformClassLoader, softWrapListenerClass) { _, method, args ->
		return@newProxyInstance if(HOOK_ON_RECALCULATION_END_METHOD == method.name && args?.size == 1){
			 onSoftWrapRecalculationEnd(args[0] as IncrementalCacheUpdateEvent)
		}else null
	}.also { editor.softWrapModel.applianceManager.addSoftWrapListener(it) }
	private var previewImg = EMPTY_IMG
	private val myRenderDirty = AtomicBoolean(false)
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
					try {
						update(renderDataList.toList())
					}finally {
						invokeLater(ModalityState.any()){
							lock.set(false)
							glancePanel.repaint()
							if (myRenderDirty.get()) {
								updateMinimapImage()
								myRenderDirty.set(false)
							}
						}
					}
				}
			}
			if(glancePanel.markCommentState.hasMarkCommentHighlight()){
				glancePanel.psiDocumentManager.performForCommittedDocument(myDocument, action)
			}else action.run()
		}else {
			myRenderDirty.compareAndSet(false,true)
		}
	}

	override fun rebuildDataAndImage() = runInEdt(modalityState){ if(canUpdate()) resetMinimapData() }

	@Suppress("UndesirableClassUsage")
	private fun update(copyList: List<LineRenderData?>){
		val pixelsPerLine = scrollState.pixelsPerLine
		val curImg = if(glancePanel.checkVisible()) {
			if(scrollState.pixelsPerLine < 1){
				getBufferedImage()
			}else {
				val height = copyList.filterNotNull().sumOf {
					it.getLineHeight(scrollState) + it.aboveBlockLine * scrollState.scale
				} + (5 * scrollState.pixelsPerLine)
				BufferedImage(glancePanel.getConfigSize().width, height.toInt(), BufferedImage.TYPE_INT_ARGB)
			}
		} else null ?: return
		val graphics = curImg.createGraphics()
		val markAttributes by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES).also {
				UISettings.setupAntialiasing(graphics)
			}
		}
		val font by lazy(LazyThreadSafetyMode.NONE) {
			editor.colorsScheme.getFont(when (markAttributes.fontType) {
				Font.ITALIC -> EditorFontType.ITALIC
				Font.BOLD -> EditorFontType.BOLD
				Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
				else -> EditorFontType.PLAIN
			}).deriveFont(config.markersScaleFactor * 3)
		}
		val docCommentRgb by lazy(LazyThreadSafetyMode.NONE){
			editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor?.rgb
		}
		val text = myDocument.immutableCharSequence
		val defaultRgb = editor.colorsScheme.defaultForeground.rgb
		var totalY = 0.0
		var skipY = 0.0
		var preSetPixelY = -1
		val curRangeList = mutableListOf<Pair<Int, Range<Double>>>()
		for ((index, it) in copyList.withIndex()) {
			if(it == null) continue
			val y = it.getLineHeight(scrollState)
			val aboveBlockLine = it.aboveBlockLine * scrollState.scale
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
						breakY@ for (renderData in it.renderData) {
							(renderData.rgb ?: defaultRgb).setColorRgb()
							for (char in renderData.renderChar) {
								curX += when (char.code) {
									9 -> 4 //TAB
									10 -> break@breakY
									else -> {
										curImg.renderImage(curX, curY, char.code)
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
						val line = myDocument.getLineNumber(foldStartOffset) - 1 + (y / scrollState.pixelsPerLine).toInt()
						val foldEndOffset = foldRegion.endOffset.run {
							if(DocumentUtil.isValidLine(line, myDocument)) {
								val lineEndOffset = myDocument.getLineEndOffset(line)
								if(this < lineEndOffset) this else lineEndOffset
							}else this
						}
						var curX = it.startX ?: 0
						var curY = totalY
						(docCommentRgb ?: defaultRgb).setColorRgb()
						for (char in CharArrayUtil.fromSequence(text, foldStartOffset, foldEndOffset)) {
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
										curImg.renderImage(curX, renderY, char.code)
									}
									curX += 1
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
						graphics.drawString(commentText, it.startX ?: 0,totalY.toInt() + (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
						skipY = font.size - if(pixelsPerLine < 1) 0.0 else pixelsPerLine
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

	private fun updateMinimapData(visLinesIterator: MyVisualLinesIterator, endVisualLine: Int){
		val text = myDocument.immutableCharSequence
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, myDocument) }
		val limitWidth = glancePanel.getConfigSize().width
		while (!visLinesIterator.atEnd()) {
			ProgressManager.checkCanceled()
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
						LineType.COMMENT, commentHighlighterEx = markCommentMap[start])
				}else if(start < text.length && text.subSequence(start, end).isNotBlank()){
					val hlIter = editor.highlighter.run {
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
					if(hlIter is OneLineHighlightDelegate || !hlIter.atEnd()){
						val renderList = mutableListOf<RenderData>()
						var foldLineIndex = visLinesIterator.getStartFoldingIndex()
						var width = 0
						do {
							ProgressManager.checkCanceled()
							var curStart = hlIter.start.run{ if(start > this) start else this }
							val curEnd = hlIter.end.run{ if(this - curStart > limitWidth) start + limitWidth else this }
							if(width > limitWidth || curEnd > text.length || curStart > curEnd) break
							//FOLD
							if(curStart == foldStartOffset){
								val foldEndOffset = foldRegion!!.endOffset
								val foldText = StringUtil.replace(foldRegion.placeholderText, "\n", " ").toCharArray()
								width += foldText.size
								renderList.add(RenderData(foldText, editor.foldingModel.placeholderAttributes?.foregroundColor?.rgb))
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
							val renderStr = CharArrayUtil.fromSequence(text, curStart, curEnd)
							width += renderStr.size
							if(renderStr.isEmpty() || renderStr.all { it.isWhitespace() }) {
								renderList.add(RenderData(renderStr))
							}else{
								val highlightList = getHighlightColor(curStart, curEnd)
								if(highlightList.isNotEmpty()){
									if(highlightList.size == 1 && highlightList.first().run{ startOffset == curStart && endOffset == curEnd }){
										renderList.add(RenderData(renderStr, highlightList.first().foregroundColor.rgb))
									}else {
										val lexerColor = runCatching { hlIter.textAttributes.foregroundColor }.getOrNull()
											?: editor.colorsScheme.defaultForeground
										var nextOffset = curStart
										var preColor: Color? = null
										for(offset in curStart .. curEnd){
											val color = highlightList.firstOrNull {
												offset >= it.startOffset && offset < it.endOffset
											}?.foregroundColor ?: lexerColor
											if(preColor != null && preColor !== color){
												renderList.add(RenderData(CharArrayUtil.fromSequence(text, nextOffset,offset), preColor.rgb))
												nextOffset = offset
											}
											preColor = color
										}
										if(nextOffset < curEnd){
											renderList.add(RenderData(CharArrayUtil.fromSequence(text, nextOffset, curEnd), preColor?.rgb))
										}
									}
								}else {
									renderList.add(RenderData(renderStr, runCatching { hlIter.textAttributes.foregroundColor?.rgb }.getOrNull()))
								}
							}
							hlIter.advance()
						}while (!hlIter.atEnd() && hlIter.start < end)
						renderDataList[visualLine] = LineRenderData(renderList.mergeSameRgbCharArray(),
							visLinesIterator.getStartsWithSoftWrap()?.indentInColumns, aboveBlockLine)
					}else {
						renderDataList[visualLine] = DefaultLineRenderData
					}
				}else {
					renderDataList[visualLine] = DefaultLineRenderData
				}
			}
			if(endVisualLine == 0 || visualLine <= endVisualLine) visLinesIterator.advance()
			else break
		}
		updateMinimapImage()
	}

	private fun MutableList<RenderData>.mergeSameRgbCharArray(): Array<RenderData> = when (size){
		0 -> emptyArray()
		1 -> arrayOf(first())
		else -> {
			var preData = get(0)
			iterator().let { iter ->
				if (iter.hasNext()) iter.next()// Skip first
				while (iter.hasNext()){
					val data = iter.next()
					if(preData.rgb == data.rgb){
						preData.renderChar += data.renderChar
						iter.remove()
					}else {
						preData = data
					}
				}
			}
			toTypedArray()
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
		myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, region.getStartOffset())
		myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, region.getEndOffset())
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
			if (!glancePanel.checkVisible() || myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode
				|| myDuringDocumentUpdate) return@invokeLaterIfNeeded
			if(highlighter.isThinErrorStripeMark.not() && (Util.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey ||
						EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))) {
				val textLength = myDocument.textLength
				val start = MathUtil.clamp(highlighter.affectedAreaStartOffset, 0, textLength)
				val end = MathUtil.clamp(highlighter.affectedAreaEndOffset, start, textLength)
				if (start != end) invalidateRange(start, end)
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

	/** HighlighterListener */
	override fun highlighterChanged(startOffset: Int, endOffset: Int) {
		invalidateRange(startOffset, endOffset)
	}

	private fun invalidateRange(startOffset: Int, endOffset: Int) {
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode) return
		val textLength = myDocument.textLength
		if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) return
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

	private fun submitUpdateMinimapDataTask(startVisualLine: Int, endVisualLine: Int, reset: Boolean) {
		if(!glancePanel.checkVisible()) return
		try {
			val visLinesIterator = MyVisualLinesIterator(editor, startVisualLine)
			if(reset){
//				println(Throwable().stackTraceToString())
				val originalStack by lazy(LazyThreadSafetyMode.NONE) { Throwable() }
				myResetDataPromise = ReadAction.nonBlocking<Unit> {
//					val startTime = System.currentTimeMillis()
					updateMinimapData(visLinesIterator, 0)
//					println("updateMinimapData time: ${System.currentTimeMillis() - startTime}")
				}.coalesceBy(this).expireWith(this).finishOnUiThread(ModalityState.any()) {
					myResetDataPromise = null
					if (myResetChangeStartOffset <= myResetChangeEndOffset) {
						doInvalidateRange(myResetChangeStartOffset, myResetChangeEndOffset)
						myResetChangeStartOffset = Int.MAX_VALUE
						myResetChangeEndOffset = Int.MIN_VALUE
						assertValidState()
					}
				}.submit(fastMinimapBackendExecutor).onError{
					myResetDataPromise = null
					if(it !is CancellationException){
						LOG.error("Async update error fileType:${virtualFile?.fileType?.name} original stack:${originalStack.stackTraceToString()}", it)
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
	private fun checkProcessReset(startOffset: Int, endOffset: Int,reset: Boolean): Boolean{
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
		if (myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode || myResetDataPromise != null || myDirty) return
		if (editor.visibleLineCount != renderDataList.size) {
			LOG.error("Inconsistent state {}", Attachment("glance.txt", editor.dumpState()))
			resetMinimapData()
			assert(editor.visibleLineCount == renderDataList.size)
		}
	}

	override fun dispose() {
		rangeList.clear()
		editor.softWrapModel.applianceManager.removeSoftWrapListener(mySoftWrapChangeListener)
		previewImg.flush()
	}

	private data class LineRenderData(val renderData: Array<RenderData>, val startX: Int?,
									  val aboveBlockLine: Int,
									  val lineType: LineType? = null,
									  val customFoldRegion: CustomFoldRegion? = null,
									  val commentHighlighterEx: RangeHighlighterEx? = null) {
		fun getLineHeight(scrollState: ScrollState) = if(lineType == LineType.CUSTOM_FOLD && customFoldRegion != null) {
			(customFoldRegion.heightInPixels * scrollState.scale).run{
				if(this < scrollState.pixelsPerLine) scrollState.pixelsPerLine else this
			}
		} else scrollState.pixelsPerLine

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
			result = 31 * result + (lineType?.hashCode() ?: 0)
			result = 31 * result + (customFoldRegion?.hashCode() ?: 0)
			result = 31 * result + (commentHighlighterEx?.hashCode() ?: 0)
			return result
		}
	}

	private data class RenderData(var renderChar: CharArray, val rgb: Int? = null){
		init {
			val index = renderChar.indexOf('\n')
			if(index > 0) {
				renderChar = renderChar.copyOfRange(0, index)
			}
		}

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

	private enum class LineType{COMMENT, CUSTOM_FOLD}

	@Suppress("UNCHECKED_CAST", "UndesirableClassUsage")
	companion object{
		private val LOG = LoggerFactory.getLogger(FastMainMinimap::class.java)
		private const val HOOK_ON_RECALCULATION_END_METHOD = "onRecalculationEnd"
		private val platformClassLoader = EditorImpl::class.java.classLoader
		private val softWrapListenerClass = arrayOf(Class.forName("com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListener"))
		private val softWrapListeners = SoftWrapApplianceManager::class.java.getDeclaredField("myListeners").apply {
			isAccessible = true
		}
		private val DefaultLineRenderData = LineRenderData(emptyArray(), null, 0)
		private val EMPTY_IMG = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB)
		private val fastMinimapBackendExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FastMinimapBackendExecutor", 1)

		private fun SoftWrapApplianceManager.addSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).add(listener)
		}

		private fun SoftWrapApplianceManager.removeSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).remove(listener)
		}
	}
}