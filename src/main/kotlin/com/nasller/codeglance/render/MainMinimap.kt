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
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.util.findParentOfType
import com.intellij.util.Alarm
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MySoftReference
import com.nasller.codeglance.util.Util
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class MainMinimap(glancePanel: GlancePanel, virtualFile: VirtualFile?): BaseMinimap(glancePanel,virtualFile){
	private val alarm by lazy {
		SingleAlarm({ updateMinimapImage() }, 500, this, Alarm.ThreadToUse.POOLED_THREAD)
	}
	private var imgReference = MySoftReference.create(getBufferedImage(), editor.editorKind != EditorKind.MAIN_EDITOR)
	override val rangeList: MutableList<Pair<Int, Range<Int>>> = mutableListOf()
	init { makeListener() }

	override fun getImageOrUpdate(): BufferedImage? {
		val img = imgReference.get()
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

	override fun rebuildDataAndImage() = updateMinimapImage(canUpdate())

	private fun getMinimapImage(): BufferedImage? {
		var curImg = imgReference.get()
		if (curImg == null || curImg.height < scrollState.documentHeight || curImg.width < glancePanel.width) {
			curImg?.flush()
			curImg = getBufferedImage()
			imgReference = MySoftReference.create(curImg, editor.editorKind != EditorKind.MAIN_EDITOR)
		}
		return if(editor.isDisposed) return null else curImg
	}

	@Suppress("UndesirableClassUsage")
	private fun getBufferedImage() = BufferedImage(glancePanel.getConfigSize().width,
		glancePanel.scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_INT_ARGB)

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
		val highlight = makeMarkHighlight(text, graphics)
		loop@ while (!hlIter.atEnd()) {
			val start = hlIter.start
			if(start > text.length) break@loop
			y = editor.document.getLineNumber(start) * config.pixelsPerLine + skipY
			val color by lazy(LazyThreadSafetyMode.NONE){ runCatching { hlIter.textAttributes.foregroundColor }.getOrNull() }
			val region = editor.foldingModel.getCollapsedRegionAtOffset(start)
			if (region != null) {
				val startLineNumber = editor.document.getLineNumber(region.startOffset)
				val endOffset = region.endOffset
				val foldLine = editor.document.getLineNumber(endOffset) - startLineNumber
				if(region !is CustomFoldRegionImpl){
					if(region.placeholderText.isNotBlank()) {
						(editor.foldingModel.placeholderAttributes?.foregroundColor ?: defaultColor).setColorRgb()
						StringUtil.replace(region.placeholderText, "\n", " ").toCharArray().forEach(moveAndRenderChar)
					}
					skipY -= foldLine * config.pixelsPerLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
				} else {
					(color ?: defaultColor).setColorRgb()
					//jump over the fold line
					val heightLine = (region.heightInPixels * scrollState.scale).toInt()
					skipY -= (foldLine + 1) * config.pixelsPerLine - heightLine
					do hlIter.advance() while (!hlIter.atEnd() && hlIter.start < endOffset)
					rangeList.add(Pair(editor.offsetToVisualLine(endOffset),
						Range(y,editor.document.getLineNumber(hlIter.start) * config.pixelsPerLine + skipY)))
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
					graphics.font = commentData.font
					graphics.drawString(commentData.comment,2,y + commentData.fontHeight)
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
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
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
					val highlightList = getHighlightColor(start, end)
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
									rangeList.add(Pair(editor.offsetToVisualLine(startOffset) - 1, Range(y, y + sumBlock)))
									y += sumBlock
									skipY += sumBlock
								}
						} }
						curImg.renderImage(x, y, charCode) {
							(highlightList.firstOrNull { offset >= it.startOffset && offset < it.endOffset }?.foregroundColor
								?: color ?: defaultColor).setColorRgb()
						}
					}
					hlIter.advance()
				}
			}
		}
		graphics.dispose()
	}

	private fun makeMarkHighlight(text: CharSequence, graphics: Graphics2D):Map<Int,MarkCommentData>{
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
		return if(markCommentMap.isNotEmpty()) {
			val lineCount = editor.document.lineCount
			val map = mutableMapOf<Int, MarkCommentData>()
			val file = glancePanel.psiDocumentManager.getCachedPsiFile(editor.document)
			val attributes = editor.colorsScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES)
			val font = editor.colorsScheme.getFont(
				when (attributes.fontType) {
					Font.ITALIC -> EditorFontType.ITALIC
					Font.BOLD -> EditorFontType.BOLD
					Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
					else -> EditorFontType.PLAIN
				}
			).deriveFont(config.markersScaleFactor * config.pixelsPerLine)
			for (highlighterEx in markCommentMap) {
				val startOffset = highlighterEx.startOffset
				file?.findElementAt(startOffset)?.findParentOfType<PsiComment>(false)?.let { comment ->
					val textRange = comment.textRange
					val commentText = text.substring(startOffset, highlighterEx.endOffset)
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(attributes.fontType, font.size2D)
					} else font
					val line = editor.document.getLineNumber(textRange.startOffset) + (config.markersScaleFactor.toInt() - 1)
					val jumpEndOffset = if (lineCount <= line) text.length else editor.document.getLineEndOffset(line)
					map[textRange.startOffset] = MarkCommentData(jumpEndOffset, commentText, textFont,
						(graphics.getFontMetrics(textFont).height / 1.5).roundToInt())
				}
			}
			graphics.color = attributes.foregroundColor ?: editor.colorsScheme.defaultForeground
			graphics.composite = GlancePanel.srcOver
			UISettings.setupAntialiasing(graphics)
			map
		} else emptyMap()
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

	override fun recalculationEnds() = Unit

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

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	override fun dispose() {
		super.dispose()
		imgReference.clear{ flush() }
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		updateMinimapImage()
	}

	private fun repaintOrRequest(request: Boolean = true) {
		if (glancePanel.checkVisible()) {
			if (request) alarm.cancelAndRequest()
			else glancePanel.repaint()
		}
	}

	private data class MarkCommentData(var jumpEndOffset: Int,val comment: String,val font: Font,val fontHeight:Int)
}