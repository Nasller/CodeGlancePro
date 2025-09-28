package com.nasller.codeglance.render

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MySoftReference
import com.nasller.codeglance.util.Util.isMarkAttributes
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class EmptyMinimap (glancePanel: GlancePanel) : BaseMinimap(glancePanel) {
	override val rangeList: MutableList<Pair<Int, Range<Double>>> = mutableListOf()
	private var imgReference = lazy {
		MySoftReference.create(getBufferedImage(scrollState), editor.editorKind != EditorKind.MAIN_EDITOR)
	}
	init { makeListener() }

	override fun getImageOrUpdate(): BufferedImage? {
		val img = imgReference.value.get()
		if(img == null) updateMinimapImage()
		return img
	}

	override fun updateMinimapImage(canUpdate: Boolean){
		if (canUpdate && !checkOutOfLineRange {
				imgReference = lazyOf(MySoftReference.create(EMPTY_IMG, false))
			} && lock.compareAndSet(false,true)) {
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
			if(glancePanel.markState.hasMarkHighlight()){
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
		if(rangeList.isNotEmpty()) rangeList.clear()
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
		glancePanel.setLineCount()
		val hlIter = editor.highlighter.createIterator(0).run {
			if(isLogFile) IdeLogFileHighlightDelegate(editor.document,this) else this
		}
		val softWrapEnable = editor.softWrapModel.isSoftWrappingEnabled
		val hasBlockInlay = editor.inlayModel.hasBlockElements()
		var y = 0.0
		var skipY = 0.0
		val moveCharIndex = moveCharIndex@{ code: Int, enterAction: (()->Unit)? ->
			if(code != 10) {
				return@moveCharIndex
			}
			y += scrollState.pixelsPerLine
			enterAction?.invoke()
		}
		val highlight = makeMarkHighlight(text, graphics)
		loop@ while (!hlIter.atEnd()) {
			val start = hlIter.start
			if(start > text.length) break@loop
			y = editor.document.getLineNumber(start) * scrollState.pixelsPerLine + skipY
			val region = editor.foldingModel.getCollapsedRegionAtOffset(start)
			if (region != null) {
				val startLineNumber = editor.document.getLineNumber(region.startOffset)
				val endOffset = region.endOffset
				val foldLine = editor.document.getLineNumber(endOffset) - startLineNumber
				if(region !is CustomFoldRegion){
					skipY -= foldLine * scrollState.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					//jump over the fold line
					val heightLine = region.heightInPixels * scrollState.scale
					skipY -= (foldLine + 1) * scrollState.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					rangeList.add(Pair(editor.offsetToVisualLine(endOffset),
						Range(y,editor.document.getLineNumber(hlIter.start) * scrollState.pixelsPerLine + skipY)))
				}
			} else {
				val commentData = highlight[start]
				if(commentData != null){
					graphics.font = commentData.font
					graphics.color = commentData.color
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
									.sumOf { it.heightInPixels * scrollState.scale }
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
		repaintOrRequest()
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
		repaintOrRequest()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
			repaintOrRequest()
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			repaintOrRequest()
		}
	}

	/** MarkupModelListener & BookmarksListener */
	override fun updateRangeHighlight(highlighter: RangeMarker) {
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || editor.foldingModel.isInBatchFoldingOperation) return
		when(highlighter){
			is MarkState.BookmarkHighlightDelegate -> repaintOrRequest()
			is RangeHighlighterEx -> {
				if(highlighter.isThinErrorStripeMark.not() && (highlighter.textAttributesKey?.isMarkAttributes() == true ||
							EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))) {
					repaintOrRequest()
				} else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
					repaintOrRequest(false)
				}
			}
		}
	}

	/** PrioritizedDocumentListener */
	override fun documentChanged(event: DocumentEvent) {
		if (event.document.isInBulkUpdate) return
		repaintOrRequest()
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		repaintOrRequest()
	}

	override fun dispose() {
		rangeList.clear()
		if(imgReference.isInitialized()){
			imgReference.value.clear{ flush() }
		}
	}

	private fun repaintOrRequest(request: Boolean = true) {
		if (glancePanel.checkVisible()) {
			if (request) updateMinimapImage()
			else glancePanel.repaint()
		}
	}
}