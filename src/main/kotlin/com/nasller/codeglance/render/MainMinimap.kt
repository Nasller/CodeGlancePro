package com.nasller.codeglance.render

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Alarm
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.SingleAlarm
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MySoftReference
import com.nasller.codeglance.util.Util
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class MainMinimap(glancePanel: GlancePanel): BaseMinimap(glancePanel){
	private val alarm by lazy {
		SingleAlarm({ updateMinimapImage() }, 500, this, Alarm.ThreadToUse.POOLED_THREAD)
	}
	private var imgReference = lazy {
		MySoftReference.create(getBufferedImage(scrollState), editor.editorKind != EditorKind.MAIN_EDITOR)
	}
	override val rangeList: MutableList<Pair<Int, Range<Double>>> = mutableListOf()
	init { makeListener() }

	override fun getImageOrUpdate(): BufferedImage? {
		val img = imgReference.value.get()
		if(img == null) updateMinimapImage()
		return img
	}

	override fun updateMinimapImage(canUpdate: Boolean){
		if (canUpdate && lock.compareAndSet(false,true)) {
			val action = Runnable {
				invokeLater(modalityState) {
					try {
						update()
					} finally {
						lock.set(false)
						glancePanel.repaint()
					}
				}
			}
			if(glancePanel.markCommentState.hasMarkCommentHighlight()){
				glancePanel.psiDocumentManager.performForCommittedDocument(editor.document, action)
			}else action.run()
		}
	}

	private fun getMinimapImage(): BufferedImage? {
		var curImg = imgReference.value.get()
		if (curImg == null || curImg.height < scrollState.documentHeight || curImg.width < glancePanel.width) {
			curImg?.flush()
			curImg = getBufferedImage(scrollState)
			imgReference = lazyOf(MySoftReference.create(curImg, editor.editorKind != EditorKind.MAIN_EDITOR))
		}
		return if(editor.isDisposed) return null else curImg
	}

	private fun update() {
		val curImg = getMinimapImage() ?: return
		if(rangeList.size > 0) rangeList.clear()
		val text = editor.document.immutableCharSequence
		val graphics = curImg.createGraphics().apply {
			setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			composite = GlancePanel.CLEAR
			fillRect(0, 0, curImg.width, curImg.height)
		}
		if(text.isEmpty()) {
			graphics.dispose()
			return
		}
		val defaultColor = editor.colorsScheme.defaultForeground
		val hlIter = editor.highlighter.createIterator(0).run {
			if(isLogFile) IdeLogFileHighlightDelegate(editor.document,this) else this
		}
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled
		val hasBlockInlay = editor.inlayModel.hasBlockElements()
		val renderHeight = scrollState.getRenderHeight()
		var x = 0
		var y = 0.0
		var preSetPixelY = -1
		var skipY = 0.0
		val moveCharIndex = { code: Int,enterAction: (()->Unit)? ->
			when (code) {
				9 -> x += 4//TAB
				10 -> {//ENTER
					x = 0
					preSetPixelY = y.toInt()
					y += scrollState.pixelsPerLine
					enterAction?.invoke()
				}
				else -> x += 1
			}
		}
		val moveAndRenderChar = { it: Char ->
			moveCharIndex(it.code, null)
			val renderY = y.toInt()
			if(renderY != preSetPixelY) {
				curImg.renderImage(x, renderY, it.code, renderHeight)
			}
		}
		val highlight = makeMarkHighlight(text, graphics)
		loop@ while (!hlIter.atEnd()) {
			val start = hlIter.start
			if(start > text.length) break@loop
			y = editor.document.getLineNumber(start) * scrollState.pixelsPerLine + skipY
			val color by lazy(LazyThreadSafetyMode.NONE){ runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() }
			val region = editor.foldingModel.getCollapsedRegionAtOffset(start)
			if (region != null) {
				val startOffset = region.startOffset
				val endOffset = region.endOffset
				val startLineNumber = editor.document.getLineNumber(startOffset)
				val foldLine = editor.document.getLineNumber(endOffset) - startLineNumber
				if(region !is CustomFoldRegionImpl){
					if(region.placeholderText.isNotBlank()) {
						(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).setColorRgb()
						StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
					}
					skipY -= foldLine * scrollState.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					(color ?: defaultColor).setColorRgb()
					//jump over the fold line
					val heightLine = region.heightInPixels * scrollState.scale
					skipY -= (foldLine + 1) * scrollState.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					rangeList.add(Pair(editor.offsetToVisualLine(endOffset),
						Range(y, editor.document.getLineNumber(hlIter.start) * scrollState.pixelsPerLine + skipY)))
					//this is render document
					val line = startLineNumber - 1 + (heightLine / scrollState.pixelsPerLine).toInt()
					text.subSequence(start, if(DocumentUtil.isValidLine(line, editor.document)){
						val lineEndOffset = editor.document.getLineEndOffset(line)
						if(endOffset < lineEndOffset || startOffset > lineEndOffset) endOffset
						else lineEndOffset
					}else endOffset).forEach(moveAndRenderChar)
				}
			} else {
				val commentData = highlight[start]
				if(commentData != null){
					graphics.font = commentData.font
					graphics.drawString(commentData.comment,2,y.toInt() + (graphics.getFontMetrics(commentData.font).height / 1.5).roundToInt())
					if (softWrapEnable) {
						val softWraps = editor.softWrapModel.getSoftWrapsForRange(start, commentData.jumpEndOffset)
						softWraps.forEachIndexed { index, softWrap ->
							softWrap.chars.forEach {char -> moveCharIndex(char.code) { skipY += scrollState.pixelsPerLine } }
							if (index == softWraps.size - 1){
								val lineEndOffset = DocumentUtil.getLineEndOffset(softWrap.end, editor.document)
								if(lineEndOffset > commentData.jumpEndOffset){
									commentData.jumpEndOffset = lineEndOffset
								}
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
									.sumOf { it.heightInPixels * scrollState.scale}
								if (sumBlock > 0) {
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
									y += sumBlock
									skipY += sumBlock
									if(lineEndOffset > commentData.jumpEndOffset){
										commentData.jumpEndOffset = lineEndOffset
									}
								}
							} }
						}
						hlIter.advance()
					}
				} else {
					val end = hlIter.end
					val highlightList = getHighlightColor(start, end)
					for(offset in start until end) {
						// Watch out for tokens that extend past the document
						if (offset >= text.length) break@loop
						if (softWrapEnable) editor.softWrapModel.getSoftWrap(offset)?.let { softWrap ->
							softWrap.chars.forEach { moveCharIndex(it.code) { skipY += scrollState.pixelsPerLine } }
						}
						val charCode = text[offset].code
						moveCharIndex(charCode) { if (hasBlockInlay) {
								val startOffset = offset + 1
								val sumBlock = editor.inlayModel.getBlockElementsInRange(startOffset, DocumentUtil.getLineEndOffset(startOffset, editor.document))
									.filter { it.placement == Inlay.Placement.ABOVE_LINE }
									.sumOf { it.heightInPixels * scrollState.scale }
								if (sumBlock > 0) {
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
									y += sumBlock
									skipY += sumBlock
								}
						} }
						val renderY = y.toInt()
						if(renderY != preSetPixelY) {
							curImg.renderImage(x, renderY, charCode, renderHeight) {
								(highlightList.firstOrNull { offset >= it.startOffset && offset < it.endOffset }?.foregroundColor ?:
								color ?: defaultColor).setColorRgb()
							}
						}
					}
					hlIter.advance()
				}
			}
		}
		graphics.dispose()
	}

	/** FoldingListener */
	override fun onFoldProcessingEnd() {
		if (editor.document.isInBulkUpdate) return
		updateMinimapImage()
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED == 0 || editor.document.isInBulkUpdate) return
		repaintOrRequest()
	}

	/** InlayModel.SimpleAdapter */
	override fun onAdded(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onRemoved(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) = checkinInlayAndUpdate(inlay, changeFlags)

	private fun checkinInlayAndUpdate(inlay: Inlay<*>, changeFlags: Int? = null) {
		if(editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Inlay.Placement.ABOVE_LINE
			|| (changeFlags != null && changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0)) return
		repaintOrRequest()
	}

	override fun onBatchModeFinish(editor: Editor) {
		if (editor.document.isInBulkUpdate) return
		updateMinimapImage()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
			updateMinimapImage()
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			updateMinimapImage()
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
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || editor.foldingModel.isInBatchFoldingOperation) return
		if(highlighter.isThinErrorStripeMark.not() && (Util.MARK_COMMENT_ATTRIBUTES == highlighter.textAttributesKey ||
			EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))) {
			repaintOrRequest()
		} else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
			repaintOrRequest(false)
		}
	}

	/** PrioritizedDocumentListener */
	override fun documentChanged(event: DocumentEvent) {
		if (event.document.isInBulkUpdate) return
		if (event.document.lineCount > 3000) {
			repaintOrRequest()
		} else updateMinimapImage()
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		updateMinimapImage()
	}

	override fun dispose() {
		rangeList.clear()
		if(imgReference.isInitialized()){
			imgReference.value.clear{ flush() }
		}
	}

	private fun repaintOrRequest(request: Boolean = true) {
		if (glancePanel.checkVisible()) {
			if (request) alarm.cancelAndRequest()
			else glancePanel.repaint()
		}
	}
}