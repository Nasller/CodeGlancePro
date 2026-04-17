package com.nasller.codeglance.render

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.psi.tree.IElementType
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.Util.mapSmart
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

abstract class BaseMinimap(protected val glancePanel: GlancePanel): InlayModel.Listener, PropertyChangeListener,
	PrioritizedDocumentListener, FoldingListener, MarkupModelListener, SoftWrapChangeListener, BookmarksListener, Disposable {
	protected val editor = glancePanel.editor
	protected val config
		get() = glancePanel.config
	protected val scrollState
		get() = glancePanel.scrollState
	protected var softWrapEnabled = false
	protected var outOfLineRange = false
	protected val modalityState
		get() = if (editor.editorKind != EditorKind.MAIN_EDITOR) ModalityState.any() else ModalityState.defaultModalityState()
	protected abstract val rangeList: MutableList<Pair<Int, Range<Double>>>
	protected val virtualFile = editor.virtualFile ?: runReadActionBlocking { glancePanel.psiDocumentManager.getCachedPsiFile(glancePanel.editor.document)?.viewProvider?.virtualFile }
	protected val isLogFile = virtualFile?.run { fileType::class.qualifiedName?.contains("ideolog") } == true
	protected val lock = AtomicBoolean(false)
	private val scaleBuffer = IntArray(4)

	abstract fun getImageOrUpdate(): BufferedImage?

	abstract fun updateMinimapImage(canUpdate: Boolean = glancePanel.checkVisible())

	open fun rebuildDataAndImage() = updateMinimapImage(canUpdate())

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	override fun recalculationEnds() = Unit

	protected abstract fun updateRangeHighlight(highlighter: RangeMarker)

	/** MarkupModelListener */
	override fun afterAdded(highlighter: RangeHighlighterEx) {
		glancePanel.markState.markHighlightChange(highlighter, false)
		updateRangeHighlight(highlighter)
	}

	override fun beforeRemoved(highlighter: RangeHighlighterEx) {
		glancePanel.markState.markHighlightChange(highlighter, true)
	}

	override fun afterRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

	/** BookmarksListener */
	override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) = bookmarkChanged(group, bookmark, false)

	override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) = bookmarkChanged(group, bookmark, true)

	override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) = bookmarkChanged(group, bookmark, false)

	private fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark, remove: Boolean){
		if(bookmark is LineBookmark && bookmark.file == virtualFile) {
			glancePanel.markState.markHighlightChange(group, bookmark, remove)?.let { updateRangeHighlight(it) }
		}
	}

	fun getMyRenderVisualLine(y: Int): Int {
		if(y <= 0) return 0
		var minus = 0.0
		for (pair in rangeList) {
			if (y.toFloat() in pair.second.from..pair.second.to) {
				return pair.first
			} else if (pair.second.to < y) {
				minus += pair.second.to - pair.second.from
			} else break
		}
		return ((y - minus) / scrollState.pixelsPerLine).toInt()
	}

	fun getMyRenderLine(lineStart: Int, lineEnd: Int): Pair<Double, Double> {
		var startAdd = 0.0
		var endAdd = 0.0
		for (pair in rangeList) {
			if (pair.first in (lineStart + 1) until lineEnd) {
				endAdd += pair.second.to - pair.second.from
			} else if (pair.first < lineStart) {
				val i = pair.second.to - pair.second.from
				startAdd += i
				endAdd += i
			}else if(pair.first == lineStart && lineStart != lineEnd){
				endAdd += pair.second.to - pair.second.from
			} else break
		}
		return startAdd to endAdd
	}

	@Suppress("UndesirableClassUsage")
	protected fun getBufferedImage(scrollState: ScrollState) = BufferedImage(
		getRasterWidth(glancePanel.getLogicalWidth()),
		getRasterBufferHeight(scrollState.documentHeight, scrollState.getRenderHeight()),
		BufferedImage.TYPE_INT_ARGB
	)

	protected fun getRasterScale(): Double = glancePanel.getPixScale()

	protected fun getRasterWidth(logicalWidth: Int, rasterScale: Double = getRasterScale()) =
		toRasterSize(logicalWidth, rasterScale)

	protected fun getRasterHeight(logicalHeight: Double, rasterScale: Double = getRasterScale()) =
		toRasterSize(logicalHeight, rasterScale)

	protected fun getRasterBufferHeight(documentHeight: Int, renderHeight: Int, rasterScale: Double = getRasterScale()) =
		toRasterBufferHeight(documentHeight, renderHeight, rasterScale)

	protected fun toRasterX(logicalX: Int, rasterScale: Double = getRasterScale()) =
		toRasterCoordinate(logicalX, rasterScale)

	protected fun toRasterY(logicalY: Int, rasterScale: Double = getRasterScale()) =
		toRasterCoordinate(logicalY, rasterScale)

	protected fun canUpdate() = glancePanel.checkVisible() && (editor.editorKind == EditorKind.CONSOLE || virtualFile == null
			|| runReadActionBlocking { editor.highlighter !is EmptyEditorHighlighter })

	protected fun getHighlightColor(startOffset: Int, endOffset: Int): List<RangeHighlightColor>{
		return if(config.syntaxHighlight && glancePanel.lineCount < 10000) runCatching {
			val list = mutableListOf<RangeHighlighterEx>()
			editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset) {
				it.getTextAttributes(editor.colorsScheme)?.foregroundColor?.apply { list.add(it) }
				return@processRangeHighlightersOverlappingWith true
			}
			if (list.size > 1) {
				ContainerUtil.quickSort(list, IterationState.createByLayerThenByAttributesComparator(editor.colorsScheme))
			}
			list.mapSmart { RangeHighlightColor(it.affectedAreaStartOffset, it.affectedAreaEndOffset, it.getTextAttributes(editor.colorsScheme)?.foregroundColor!!) }
		}.getOrElse { emptyList() }
		else emptyList()
	}

	protected fun Color.setColorRgb() {
		scaleBuffer[0] = red
		scaleBuffer[1] = green
		scaleBuffer[2] = blue
	}

	protected fun Int.setColorRgb() {
		scaleBuffer[0] = (this shr 16) and 0xFF //RED
		scaleBuffer[1] = (this shr 8) and 0xFF //GREEN
		scaleBuffer[2] = (this shr 0) and 0xFF //BLUE
	}

	protected fun BufferedImage.renderImage(
		x: Int,
		y: Int,
		char: Int,
		pixelsPerLine: Int,
		rasterScale: Double = getRasterScale(),
		consumer: (() -> Unit)? = null
	) {
		val rasterXStart = toRasterX(x, rasterScale)
		val rasterXEnd = max(rasterXStart + 1, toRasterX(x + 1, rasterScale))
		val rasterYStart = toRasterY(y, rasterScale)
		val rasterYEnd = max(rasterYStart + 1, toRasterY(y + pixelsPerLine, rasterScale))
		if (char !in 0..32 && rasterXStart < width && rasterXEnd > 0 && rasterYStart >= 0 && rasterYEnd <= height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(rasterXStart, rasterXEnd, rasterYStart, rasterYEnd, char, pixelsPerLine)
			} else {
				renderAccurate(rasterXStart, rasterXEnd, rasterYStart, rasterYEnd, char, pixelsPerLine)
			}
		}
	}

	private fun BufferedImage.renderClean(
		xStart: Int,
		xEnd: Int,
		yStart: Int,
		yEnd: Int,
		char: Int,
		pixelsPerLine: Int
	) {
		val weight = when (char) {
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		val weights = when (pixelsPerLine.coerceIn(1, 4)) {
			1 -> floatArrayOf(weight * 0.6f)
			2 -> floatArrayOf(weight * 0.3f, weight * 0.6f)
			3 -> floatArrayOf(weight * 0.1f, weight * 0.6f, weight * 0.6f)
			else -> floatArrayOf(0f, weight * 0.6f, weight * 0.6f, weight * 0.6f)
		}
		fillRasterizedPixels(xStart, xEnd, yStart, yEnd, weights)
	}

	private fun BufferedImage.renderAccurate(
		xStart: Int,
		xEnd: Int,
		yStart: Int,
		yEnd: Int,
		char: Int,
		pixelsPerLine: Int
	) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		val weights = when (pixelsPerLine.coerceIn(1, 4)) {
			1 -> floatArrayOf((topWeight + bottomWeight) / 2)
			2 -> floatArrayOf(topWeight * 0.5f, bottomWeight)
			3 -> floatArrayOf(topWeight * 0.3f, (topWeight + bottomWeight) / 2, bottomWeight * 0.7f)
			else -> floatArrayOf(0f, topWeight, (topWeight + bottomWeight) / 2, bottomWeight)
		}
		fillRasterizedPixels(xStart, xEnd, yStart, yEnd, weights)
	}

	private fun BufferedImage.fillRasterizedPixels(
		xStart: Int,
		xEnd: Int,
		yStart: Int,
		yEnd: Int,
		baseWeights: FloatArray
	) {
		val rasterHeight = yEnd - yStart
		if (rasterHeight <= 0 || xEnd <= xStart) return
		for (row in 0 until rasterHeight) {
			val weightIndex = (((row + 0.5) * baseWeights.size) / rasterHeight).toInt().coerceIn(0, baseWeights.lastIndex)
			val alpha = baseWeights[weightIndex]
			if (alpha <= 0f) continue
			for (col in xStart until xEnd) {
				setPixel(col, yStart + row, alpha)
			}
		}
	}

	/**
	 * mask out the alpha component and set it to the given value.
	 * *
	 * @param alpha     alpha percent from 0-1.
	 */
	private fun BufferedImage.setPixel(x: Int, y: Int, alpha: Float) {
		scaleBuffer[3] = (alpha * 0xFF).toInt()
		raster.setPixel(x, y, scaleBuffer)
	}

	protected fun createMarkFont(attributes: TextAttributes): Font {
		return editor.colorsScheme.getFont(when (attributes.fontType) {
			Font.BOLD -> EditorFontType.BOLD
			Font.ITALIC -> EditorFontType.BOLD_ITALIC
			Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
			else -> EditorFontType.BOLD
		}).deriveFont(config.markersScaleFactor * 3)
	}

	protected fun createMarkTextFont(commentText: String, baseFont: Font, fontType: Int): Font {
		return if (!SystemInfoRt.isMac && baseFont.canDisplayUpTo(commentText) != -1) {
			UIUtil.getFontWithFallback(baseFont).deriveFont(fontType, baseFont.size2D)
		} else {
			baseFont
		}
	}

	protected fun configureMarkGraphics(graphics: Graphics2D, pixScale: Double, scaleGraphics: Boolean = true) {
		graphics.composite = GlancePanel.srcOver
		EditorUIUtil.setupAntialiasing(graphics)
		if (scaleGraphics) {
			graphics.scale(pixScale, pixScale)
		}
	}

	protected fun computeMarkBaseline(logicalY: Double, font: Font, pixelsPerLine: Double, pixScale: Double): Int {
		return computeMarkBaseline(logicalY, font.size, pixelsPerLine, pixScale)
	}

	protected fun computeMarkOccupiedHeight(font: Font, pixScale: Double): Double {
		return computeMarkOccupiedHeight(font.size, pixScale)
	}

	protected fun computeMarkOverflowHeight(font: Font, pixelsPerLine: Double, pixScale: Double): Double {
		val currentLineHeight = if (pixelsPerLine < 1) 0.0 else pixelsPerLine
		return (computeMarkOccupiedHeight(font, pixScale) - currentLineHeight).coerceAtLeast(0.0)
	}

	protected fun computeMarkEndLine(startLine: Int, font: Font, pixelsPerLine: Double, pixScale: Double): Int {
		return startLine + (computeMarkOccupiedHeight(font, pixScale) / pixelsPerLine).toInt()
	}

	protected fun makeListener(){
		Disposer.register(glancePanel,this)
		editor.addPropertyChangeListener(this,this)
		editor.document.addDocumentListener(this, this)
		editor.foldingModel.addListener(this, this)
		editor.inlayModel.addListener(this, this)
		editor.softWrapModel.addSoftWrapChangeListener(this)
		editor.filteredDocumentMarkupModel.addMarkupModelListener(this, this)
		glancePanel.project.messageBus.connect(this).subscribe(BookmarksListener.TOPIC, this)
	}

	protected fun makeMarkHighlight(text: CharSequence, graphics: Graphics2D):Map<Int,MarkCommentData>{
		val markCommentMap = glancePanel.markState.getAllMarkHighlight()
		return if(markCommentMap.isNotEmpty()) {
			val lineCount = editor.document.lineCount
			val map = mutableMapOf<Int, MarkCommentData>()
			val file = runReadActionBlocking { glancePanel.psiDocumentManager.getCachedPsiFile(editor.document) }
			val pixScale = glancePanel.getPixScale()
			for (rangeMarker in markCommentMap) {
				val attributes = rangeMarker.getTextAttributes(editor.colorsScheme)!!
				val font = createMarkFont(attributes)
				val startOffset = rangeMarker.startOffset
				runReadActionBlocking {
					file?.findElementAt(startOffset)?.let { comment ->
						val textRange = if (rangeMarker is MarkState.BookmarkHighlightDelegate)
							comment.nextSibling?.textRange ?: comment.textRange else comment.textRange
						val commentText = rangeMarker.getUserData(MarkState.BOOK_MARK_DESC_KEY) ?: text.substring(startOffset, rangeMarker.endOffset).trim()
						val textFont = createMarkTextFont(commentText, font, attributes.fontType)
						val line = computeMarkEndLine(editor.document.getLineNumber(textRange.startOffset), textFont, scrollState.pixelsPerLine, pixScale)
						val jumpEndOffset = if (lineCount <= line) text.length else editor.document.getLineEndOffset(line)
						map[textRange.startOffset] = MarkCommentData(jumpEndOffset, commentText, textFont, attributes.errorStripeColor)
					}
				}
			}
			configureMarkGraphics(graphics, pixScale)
			map
		} else emptyMap()
	}

	protected fun checkOutOfLineRange(action: () -> Unit): Boolean {
		if (outOfLineRange) {
			return true
		}else {
			outOfLineRange = editor.document.lineCount !in config.minLinesCount..config.maxLinesCount && config.outLineEmpty
			if(outOfLineRange) {
				rangeList.clear()
				glancePanel.markState.clear()
				action.invoke()
				return true
			}
		}
		return false
	}

	protected data class RangeHighlightColor(val startOffset: Int,val endOffset: Int,val foregroundColor: Color)

	protected class IdeLogFileHighlightDelegate(private val myDocument: Document,private val highlighterIterator: HighlighterIterator)
		: HighlighterIterator by highlighterIterator{
		override fun getTextAttributesKeys(): Array<out TextAttributesKey?> {
			return highlighterIterator.getTextAttributesKeys()
		}

		override fun getEnd(): Int {
			val end = highlighterIterator.end
			return if(DocumentUtil.isAtLineEnd(end, myDocument) && end + 1 < myDocument.textLength) end + 1
			else end
		}
	}

	protected class OneLineHighlightDelegate(text: CharSequence, private val startOffset: Int, private var endOffset: Int) : HighlighterIterator {
		private var start = startOffset
		private val offsetLineIterator = text.subSequence(startOffset,endOffset)
			.withIndex().filter { it.value == '\n' }.map { it.index }.iterator()
		init {
			if(offsetLineIterator.hasNext()){
				endOffset = offsetLineIterator.next() + startOffset
			}
		}

		override fun getTextAttributes(): TextAttributes = TextAttributes.ERASE_MARKER

		override fun getStart() = start

		override fun getEnd() = endOffset

		override fun getTokenType(): IElementType = IElementType.find(IElementType.FIRST_TOKEN_INDEX)

		override fun advance() = Unit

		override fun retreat() = Unit

		override fun atEnd(): Boolean {
			val hasNext = offsetLineIterator.hasNext()
			return if(hasNext) {
				start = endOffset
				endOffset = offsetLineIterator.next() + startOffset
				false
			} else true
		}

		override fun getDocument() = throw UnsupportedOperationException()
	}

	protected data class MarkCommentData(var jumpEndOffset: Int, val comment: String, val font: Font, val color: Color)

	@Suppress("UndesirableClassUsage")
	companion object{
		val EMPTY_IMG = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB)

		internal fun toRasterSize(logicalSize: Int, rasterScale: Double): Int {
			return max(1, (logicalSize * rasterScale).roundToInt())
		}

		internal fun toRasterSize(logicalSize: Double, rasterScale: Double): Int {
			return max(1, (logicalSize * rasterScale).roundToInt())
		}

		internal fun toRasterCoordinate(logicalCoordinate: Int, rasterScale: Double): Int {
			return (logicalCoordinate * rasterScale).roundToInt()
		}

		internal fun fromRasterSize(rasterSize: Int, rasterScale: Double): Int {
			return max(1, (rasterSize / rasterScale).roundToInt())
		}

		internal fun toRasterBufferHeight(documentHeight: Int, renderHeight: Int, rasterScale: Double): Int {
			return toRasterSize(documentHeight + (100 * renderHeight), rasterScale)
		}

		@Suppress("UNUSED_PARAMETER")
		internal fun computeMarkBaseline(logicalY: Double, fontSize: Int, pixelsPerLine: Double, pixScale: Double): Int {
			return (logicalY + computeMarkOccupiedHeight(fontSize, pixScale)).toInt()
		}

		@Suppress("UNUSED_PARAMETER")
		internal fun computeMarkOccupiedHeight(fontSize: Int, pixScale: Double): Double {
			return fontSize.toDouble()
		}

		internal fun shouldRecreateImage(
			curImg: BufferedImage?,
			documentHeight: Int,
			logicalWidth: Int,
			renderHeight: Int,
			rasterScale: Double
		): Boolean {
			return curImg == null ||
				curImg.height < toRasterBufferHeight(documentHeight, renderHeight, rasterScale) ||
				curImg.width != toRasterSize(logicalWidth, rasterScale)
		}

		fun EditorKind.getMinimap(glancePanel: GlancePanel): BaseMinimap = glancePanel.run {
			if(config.useEmptyMinimapStr.contains(this@getMinimap.name)) {
				return EmptyMinimap(this)
			}
			return if(this@getMinimap != EditorKind.UNTYPED && this@getMinimap != EditorKind.CONSOLE && config.useFastMinimapForMain) {
				FastMainMinimap(this)
			}else {
				MainMinimap(this)
			}
		}
	}
}
