package com.nasller.codeglance.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorFontType
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
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.findParentOfType
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.Util.mapSmart
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseMinimap(protected val glancePanel: GlancePanel): InlayModel.Listener, PropertyChangeListener,
	PrioritizedDocumentListener, FoldingListener, MarkupModelListener, SoftWrapChangeListener, Disposable {
	protected val editor = glancePanel.editor
	protected val config
		get() = glancePanel.config
	protected val scrollState
		get() = glancePanel.scrollState
	protected var softWrapEnabled = false
	protected val modalityState
		get() = if (editor.editorKind != EditorKind.MAIN_EDITOR) ModalityState.any() else ModalityState.defaultModalityState()
	protected abstract val rangeList: MutableList<Pair<Int, Range<Double>>>
	protected val virtualFile = editor.virtualFile ?: glancePanel.psiDocumentManager.getPsiFile(glancePanel.editor.document)?.virtualFile
	protected val isLogFile = virtualFile?.run { fileType::class.qualifiedName?.contains("ideolog") } ?: false
	protected val lock = AtomicBoolean(false)
	private val scaleBuffer = IntArray(4)

	abstract fun getImageOrUpdate(): BufferedImage?

	abstract fun updateMinimapImage(canUpdate: Boolean = glancePanel.checkVisible())

	open fun rebuildDataAndImage() = updateMinimapImage(canUpdate())

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	override fun recalculationEnds() = Unit

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

	protected fun getBufferedImage(scrollState: ScrollState) = ImageUtil.createImage(glancePanel.graphicsConfiguration, glancePanel.getConfigSize().width,
			scrollState.documentHeight + (100 * scrollState.getRenderHeight()), BufferedImage.TYPE_INT_ARGB)

	protected fun canUpdate() = glancePanel.checkVisible() && (editor.editorKind == EditorKind.CONSOLE || virtualFile == null
			|| runReadAction { editor.highlighter !is EmptyEditorHighlighter })

	protected fun getHighlightColor(startOffset: Int, endOffset: Int): List<RangeHighlightColor>{
		return if(config.syntaxHighlight) runCatching {
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

	protected fun BufferedImage.renderImage(x: Int, y: Int, char: Int,pixelsPerLine: Int, consumer: (() -> Unit)? = null) {
		if (char !in 0..32 && x in 0 until width && 0 <= y && y + pixelsPerLine < height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(x, y, char, pixelsPerLine)
			} else {
				renderAccurate(x, y, char, pixelsPerLine)
			}
		}
	}

	private fun BufferedImage.renderClean(x: Int, y: Int, char: Int, pixelsPerLine: Int) {
		val weight = when (char) {
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		when (pixelsPerLine) {
			// Can't show space between lines anymore. This looks rather ugly...
			1 -> setPixel(x, y + 1, weight * 0.6f)
			// Two lines we make the top line a little lighter to give the illusion of space between lines.
			2 -> {
				setPixel(x, y, weight * 0.3f)
				setPixel(x, y + 1, weight * 0.6f)
			}
			// Three lines we make the top nearly empty, and fade the bottom a little too
			3 -> {
				setPixel(x, y, weight * 0.1f)
				setPixel(x, y + 1, weight * 0.6f)
				setPixel(x, y + 2, weight * 0.6f)
			}
			// Empty top line, Nice blend for everything else
			4 -> {
				setPixel(x, y + 1, weight * 0.6f)
				setPixel(x, y + 2, weight * 0.6f)
				setPixel(x, y + 3, weight * 0.6f)
			}
		}
	}

	private fun BufferedImage.renderAccurate(x: Int, y: Int, char: Int, pixelsPerLine: Int) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		when (pixelsPerLine) {
			// Can't show space between lines anymore. This looks rather ugly...
			1 -> setPixel(x, y + 1, (topWeight + bottomWeight) / 2)
			// Two lines we make the top line a little lighter to give the illusion of space between lines.
			2 -> {
				setPixel(x, y, topWeight * 0.5f)
				setPixel(x, y + 1, bottomWeight)
			}
			// Three lines we make the top nearly empty, and fade the bottom a little too
			3 -> {
				setPixel(x, y, topWeight * 0.3f)
				setPixel(x, y + 1, (topWeight + bottomWeight) / 2)
				setPixel(x, y + 2, bottomWeight * 0.7f)
			}
			// Empty top line, Nice blend for everything else
			4 -> {
				setPixel(x, y + 1, topWeight)
				setPixel(x, y + 2, (topWeight + bottomWeight) / 2)
				setPixel(x, y + 3, bottomWeight)
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

	protected fun makeListener(){
		Disposer.register(glancePanel,this)
		editor.addPropertyChangeListener(this,this)
		editor.document.addDocumentListener(this, this)
		editor.foldingModel.addListener(this, this)
		editor.inlayModel.addListener(this, this)
		editor.softWrapModel.addSoftWrapChangeListener(this)
		editor.filteredDocumentMarkupModel.addMarkupModelListener(this, this)
	}

	protected fun makeMarkHighlight(text: CharSequence, graphics: Graphics2D):Map<Int,MarkCommentData>{
		val markCommentMap = glancePanel.markCommentState.getAllMarkCommentHighlight()
		return if(markCommentMap.isNotEmpty()) {
			val lineCount = editor.document.lineCount
			val map = mutableMapOf<Int, MarkCommentData>()
			val file = glancePanel.psiDocumentManager.getCachedPsiFile(editor.document)
			val attributes = editor.colorsScheme.getAttributes(Util.MARK_COMMENT_ATTRIBUTES)
			val pixScale = glancePanel.scaleContext.getScale(DerivedScaleType.PIX_SCALE)
			val font = editor.colorsScheme.getFont(
				when (attributes.fontType) {
					Font.ITALIC -> EditorFontType.ITALIC
					Font.BOLD -> EditorFontType.BOLD
					Font.ITALIC or Font.BOLD -> EditorFontType.BOLD_ITALIC
					else -> EditorFontType.PLAIN
				}
			).deriveFont(config.markersScaleFactor * 3 / pixScale.toFloat())
			for (highlighterEx in markCommentMap) {
				val startOffset = highlighterEx.startOffset
				file?.findElementAt(startOffset)?.findParentOfType<PsiComment>(false)?.let { comment ->
					val textRange = comment.textRange
					val commentText = text.substring(startOffset, highlighterEx.endOffset)
					val textFont = if (!SystemInfoRt.isMac && font.canDisplayUpTo(commentText) != -1) {
						UIUtil.getFontWithFallback(font).deriveFont(attributes.fontType, font.size2D)
					} else font
					val line = editor.document.getLineNumber(textRange.startOffset) + (textFont.size / scrollState.pixelsPerLine * pixScale).toInt()
					val jumpEndOffset = if (lineCount <= line) text.length else editor.document.getLineEndOffset(line)
					map[textRange.startOffset] = MarkCommentData(jumpEndOffset, commentText, textFont)
				}
			}
			graphics.composite = GlancePanel.srcOver
			graphics.color = attributes.foregroundColor ?: editor.colorsScheme.defaultForeground
			EditorUIUtil.setupAntialiasing(graphics)
			GraphicsUtil.setupAAPainting(graphics)
			graphics.scale(pixScale, pixScale)
			map
		} else emptyMap()
	}

	protected data class RangeHighlightColor(val startOffset: Int,val endOffset: Int,val foregroundColor: Color)

	protected class IdeLogFileHighlightDelegate(private val myDocument: Document,private val highlighterIterator: HighlighterIterator)
		: HighlighterIterator by highlighterIterator{
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

	protected data class MarkCommentData(var jumpEndOffset: Int, val comment: String, val font: Font)

	companion object{
		fun EditorKind.getMinimap(glancePanel: GlancePanel): BaseMinimap = glancePanel.run {
			if(config.useEmptyMinimap.contains(this@getMinimap)) {
				return EmptyMinimap(this)
			}
			return if(this@getMinimap == EditorKind.MAIN_EDITOR && config.useFastMinimapForMain) {
				FastMainMinimap(this)
			}else {
				MainMinimap(this)
			}
		}
	}
}