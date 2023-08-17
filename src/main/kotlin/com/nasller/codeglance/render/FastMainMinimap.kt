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
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
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
	@Volatile
	private var previewImg = EMPTY_IMG
	@Volatile
	private var myResetDataPromise: CancellablePromise<Unit>? = null
	private var myRenderDirty = false
	init { makeListener() }

	override fun getImageOrUpdate() = previewImg

	override fun updateMinimapImage(canUpdate: Boolean){
		if (canUpdate && myDocument.textLength > 0) {
			if(lock.compareAndSet(false,true)) {
				val action = Runnable {
					ApplicationManager.getApplication().executeOnPooledThread {
						update(renderDataList.toList())
						invokeLater(ModalityState.any()){
							glancePanel.repaint()
							lock.set(false)
							if (myRenderDirty) {
								myRenderDirty = false
								updateMinimapImage()
							}
						}
					}
				}
				if(editor.editorKind != EditorKind.CONSOLE){
					glancePanel.psiDocumentManager.performForCommittedDocument(myDocument, action)
				}else action.run()
			}else {
				myRenderDirty = true
			}
		}
	}

	override fun rebuildDataAndImage() {
		runInEdt(modalityState){ if(canUpdate()) resetMinimapData() }
	}

	@Suppress("UndesirableClassUsage")
	private fun update(copyList: List<LineRenderData?>){
		val curImg = if(glancePanel.checkVisible()) BufferedImage(glancePanel.getConfigSize().width,
			copyList.filterNotNull().sumOf { (it.y ?: config.pixelsPerLine) + (it.aboveBlockLine ?: 0) }, BufferedImage.TYPE_INT_ARGB)
		else null ?: return
		val graphics = curImg.createGraphics()
		graphics.composite = GlancePanel.CLEAR
		graphics.fillRect(0, 0, curImg.width, curImg.height)
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
			}).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
		}
		val defaultRgb = editor.colorsScheme.defaultForeground.rgb
		var totalY = 0
		var skipY = 0
		val curRangeList = mutableListOf<Pair<Int, Range<Int>>>()
		for ((index, it) in copyList.withIndex()) {
			if(it == null) continue
			val y = it.y ?: config.pixelsPerLine
			val aboveBlockLine = it.aboveBlockLine ?: 0
			//Coordinates
			if(it.lineType == LineType.CUSTOM_FOLD){
				curRangeList.add(index to Range(totalY, totalY + y - config.pixelsPerLine + aboveBlockLine))
			}else if(aboveBlockLine > 0){
				curRangeList.add(index - 1 to Range(totalY, totalY + y - config.pixelsPerLine + aboveBlockLine))
			}
			//Skipping
			if(skipY > 0){
				if(skipY in 1 .. aboveBlockLine){
					totalY += aboveBlockLine
					skipY = 0
				}else {
					val curY = aboveBlockLine + y
					totalY += curY
					skipY -= curY
					continue
				}
			}else {
				totalY += aboveBlockLine
			}
			//Rendering
			if(it !== DefaultLineRenderData){
				when(it.lineType){
					null, LineType.CUSTOM_FOLD -> {
						var curX = it.startX ?: 0
						var curY = totalY
						breakY@ for (renderData in it.renderData) {
							(renderData.rgb ?: defaultRgb).setColorRgb()
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
						graphics.drawString(commentText, it.startX ?: 0,totalY + (graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
						skipY = (config.markersScaleFactor.toInt() - 1) * config.pixelsPerLine
					}
				}
			}
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
		val docComment by lazy(LazyThreadSafetyMode.NONE){
			editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT).foregroundColor
		}
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
			.associateBy { DocumentUtil.getLineStartOffset(it.startOffset, myDocument) }
		val limitWidth = glancePanel.getConfigSize().width
		while (!visLinesIterator.atEnd()) {
			val start = visLinesIterator.getVisualLineStartOffset()
			val end = visLinesIterator.getVisualLineEndOffset()
			val visualLine = visLinesIterator.getVisualLine()
			if(myResetDataPromise != null) {
				ProgressManager.checkCanceled()
				//Check invalid somethings in background task
				if(visualLine >= renderDataList.size || start > end) return
			}
			//BLOCK_INLAY
			val aboveBlockLine = visLinesIterator.getBlockInlaysAbove().sumOf { (it.heightInPixels * scrollState.scale).toInt() }
				.run { if(this > 0) this else null }
			//CUSTOM_FOLD
			var foldRegion = visLinesIterator.getCurrentFoldRegion()
			var foldStartOffset = foldRegion?.startOffset ?: -1
			if(foldRegion is CustomFoldRegion && foldStartOffset == start){
				//jump over the fold line
				val heightLine = (foldRegion.heightInPixels * scrollState.scale).toInt().run{
					if(this < config.pixelsPerLine) config.pixelsPerLine else this
				}
				//this is render document
				val line = myDocument.getLineNumber(foldStartOffset) - 1 + (heightLine / config.pixelsPerLine)
				val foldEndOffset = foldRegion.endOffset.run {
					if(DocumentUtil.isValidLine(line, myDocument)) {
						val lineEndOffset = myDocument.getLineEndOffset(line)
						if(this < lineEndOffset) this else lineEndOffset
					}else this
				}
				renderDataList[visualLine] = LineRenderData(arrayOf(RenderData(CharArrayUtil.fromSequence(text, foldStartOffset,
					foldEndOffset), docComment?.rgb)), null, heightLine, aboveBlockLine, LineType.CUSTOM_FOLD)
			}else {
				//COMMENT
				if(markCommentMap.containsKey(start)) {
					renderDataList[visualLine] = LineRenderData(emptyArray(), 2, null, aboveBlockLine,
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
							visLinesIterator.getStartsWithSoftWrap()?.indentInColumns, null, aboveBlockLine)
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

	private fun MutableList<RenderData>.mergeSameRgbCharArray(): Array<RenderData>{
		return if(isNotEmpty()){
			if(size > 1){
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
			}else arrayOf(first())
		}else emptyArray()
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
		doInvalidateRange(startOffset, startOffset)
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
			if (!glancePanel.checkVisible() || myDocument.isInBulkUpdate || editor.inlayModel.isInBatchMode ||
				editor.foldingModel.isInBatchFoldingOperation || myDuringDocumentUpdate) return@invokeLaterIfNeeded
			if(highlighter.isThinErrorStripeMark.not() && (Util.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey ||
						EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))) {
				val textLength = myDocument.textLength
				val startOffset = MathUtil.clamp(highlighter.affectedAreaStartOffset, 0, textLength)
				val endOffset = MathUtil.clamp(highlighter.affectedAreaEndOffset, 0, textLength)
				if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) return@invokeLaterIfNeeded
				if(myDuringDocumentUpdate) {
					myDocumentChangeStartOffset = min(myDocumentChangeStartOffset, startOffset)
					myDocumentChangeEndOffset = max(myDocumentChangeEndOffset, endOffset)
				}else if (myFoldingChangeEndOffset != Int.MIN_VALUE) {
					myFoldingChangeStartOffset = min(myFoldingChangeStartOffset, startOffset)
					myFoldingChangeEndOffset = max(myFoldingChangeEndOffset, endOffset)
				}else {
					doInvalidateRange(startOffset, endOffset)
				}
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
				val originalStack = Throwable()
				myResetDataPromise = ReadAction.nonBlocking<Unit> {
//					val startTime = System.currentTimeMillis()
					try {
						updateMinimapData(visLinesIterator, 0)
					}catch (e: Throwable){
						if(e !is ProcessCanceledException){
							LOG.error("Async update error fileType:${virtualFile?.fileType?.name} original stack:${originalStack.stackTraceToString()}", e)
						}
						throw e
					}
//					println("updateMinimapData time: ${System.currentTimeMillis() - startTime}")
				}.coalesceBy(this).expireWith(this).finishOnUiThread(ModalityState.any()) {
					myResetDataPromise = null
					if (myResetChangeStartOffset <= myResetChangeEndOffset) {
						doInvalidateRange(myResetChangeStartOffset, myResetChangeEndOffset)
						myResetChangeStartOffset = Int.MAX_VALUE
						myResetChangeEndOffset = Int.MIN_VALUE
						assertValidState()
					}
				}.submit(AppExecutorUtil.getAppExecutorService())
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
		super.dispose()
		editor.softWrapModel.applianceManager.removeSoftWrapListener(mySoftWrapChangeListener)
		previewImg.flush()
	}

	private data class LineRenderData(val renderData: Array<RenderData>, val startX: Int?, var y: Int?, val aboveBlockLine: Int?,
									  val lineType: LineType? = null, val commentHighlighterEx: RangeHighlighterEx? = null) {
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
			result = 31 * result + (startX ?: 0)
			result = 31 * result + (y ?: 0)
			result = 31 * result + (aboveBlockLine ?: 0)
			result = 31 * result + (lineType?.hashCode() ?: 0)
			result = 31 * result + (commentHighlighterEx?.hashCode() ?: 0)
			return result
		}
	}

	private data class RenderData(var renderChar: CharArray, val rgb: Int? = null){
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
		private val DefaultLineRenderData = LineRenderData(emptyArray(), null, null, null)
		private val EMPTY_IMG = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB)

		private fun SoftWrapApplianceManager.addSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).add(listener)
		}

		private fun SoftWrapApplianceManager.removeSoftWrapListener(listener: Any) {
			(softWrapListeners.get(this) as MutableList<Any>).remove(listener)
		}
	}
}