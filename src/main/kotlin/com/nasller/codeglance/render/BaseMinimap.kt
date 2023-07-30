package com.nasller.codeglance.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.psi.tree.IElementType
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.util.MySoftReference
import java.awt.Color
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseMinimap(protected val glancePanel: GlancePanel): InlayModel.SimpleAdapter(), PropertyChangeListener,
	PrioritizedDocumentListener, FoldingListener, MarkupModelListener, SoftWrapChangeListener, Disposable {
	protected val editor
		get() = glancePanel.editor
	protected val config
		get() = glancePanel.config
	protected val scrollState
		get() = glancePanel.scrollState
	protected var softWrapEnabled = false
	protected val modalityState
		get() = if (editor.editorKind != EditorKind.MAIN_EDITOR) ModalityState.any() else ModalityState.NON_MODAL
	protected val rangeList by lazy(LazyThreadSafetyMode.NONE) { mutableListOf<Pair<Int, Range<Int>>>() }
	private val scaleBuffer = FloatArray(4)
	private val lock = AtomicBoolean(false)
	private var imgReference = MySoftReference.create(getBufferedImage(), editor.editorKind != EditorKind.MAIN_EDITOR)

	abstract fun update()

	abstract fun rebuildDataAndImage()

	fun getImageOrUpdate() : BufferedImage? {
		val img = imgReference.get()
		if(img == null) updateImage()
		return img
	}

	fun updateImage(canUpdate: Boolean = glancePanel.checkVisible(), directUpdate: Boolean = false){
		if (canUpdate && lock.compareAndSet(false,true)) {
			glancePanel.psiDocumentManager.performForCommittedDocument(editor.document) {
				if (directUpdate) updateImgTask()
				else invokeLater(modalityState){ updateImgTask() }
			}
		}
	}

	private fun updateImgTask() {
		try {
			update()
		} finally {
			lock.set(false)
			glancePanel.repaint()
		}
	}

	fun getMyRenderVisualLine(y: Int): Int {
		var minus = 0
		for (pair in rangeList) {
			if (y in pair.second.from..pair.second.to) {
				return pair.first
			} else if (pair.second.to < y) {
				minus += pair.second.to - pair.second.from
			} else break
		}
		return (y - minus) / config.pixelsPerLine
	}

	fun getMyRenderLine(lineStart: Int, lineEnd: Int): Pair<Int, Int> {
		var startAdd = 0
		var endAdd = 0
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

	protected fun canUpdate() = glancePanel.checkVisible() &&
			(editor.editorKind == EditorKind.CONSOLE || runReadAction { editor.highlighter !is EmptyEditorHighlighter })

	protected fun getMinimapImage() : BufferedImage? {
		var curImg = imgReference.get()
		if (curImg == null || curImg.height < scrollState.documentHeight || curImg.width < glancePanel.width) {
			curImg?.flush()
			curImg = getBufferedImage()
			imgReference = MySoftReference.create(curImg, editor.editorKind != EditorKind.MAIN_EDITOR)
		}
		return if (editor.isDisposed || editor.document.lineCount <= 0) return null else curImg
	}

	protected fun getHighlightColor(startOffset:Int,endOffset:Int):MutableList<RangeHighlightColor>{
		val list = mutableListOf<RangeHighlightColor>()
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(startOffset,endOffset) {
			val foregroundColor = it.getTextAttributes(editor.colorsScheme)?.foregroundColor
			if (foregroundColor != null) list.add(RangeHighlightColor(it.startOffset,it.endOffset,foregroundColor))
			return@processRangeHighlightersOverlappingWith true
		}
		return list
	}

	protected fun Color.setColorRgba() {
		scaleBuffer[0] = red.toFloat()
		scaleBuffer[1] = green.toFloat()
		scaleBuffer[2] = blue.toFloat()
	}

	protected fun BufferedImage.renderImage(x: Int, y: Int, char: Int, consumer: (() -> Unit)? = null) {
		if (char !in 0..32 && x in 0 until width && 0 <= y && y + config.pixelsPerLine < height) {
			consumer?.invoke()
			if (config.clean) {
				renderClean(x, y, char)
			} else {
				renderAccurate(x, y, char)
			}
		}
	}

	private fun BufferedImage.renderClean(x: Int, y: Int, char: Int) {
		val weight = when (char) {
			in 33..126 -> 0.8f
			else -> 0.4f
		}
		when (config.pixelsPerLine) {
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

	private fun BufferedImage.renderAccurate(x: Int, y: Int, char: Int) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		when (config.pixelsPerLine) {
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
		scaleBuffer[3] = alpha * 0xFF
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

	override fun dispose() {
		imgReference.clear{ flush() }
		rangeList.clear()
	}

	@Suppress("UndesirableClassUsage")
	private fun getBufferedImage() = BufferedImage(glancePanel.getConfigSize().width, glancePanel.scrollState.documentHeight + (100 * config.pixelsPerLine), BufferedImage.TYPE_4BYTE_ABGR)

	protected data class RangeHighlightColor(val startOffset: Int,val endOffset: Int,val foregroundColor: Color)

	protected class IdeLogFileHighlightDelegate(private val document: Document, private val highlighterIterator: HighlighterIterator)
		: HighlighterIterator by highlighterIterator{
		private val length = document.textLength

		override fun getEnd(): Int {
			val end = highlighterIterator.end
			return if(DocumentUtil.isAtLineEnd(end,document) && end + 1 < length) end + 1
			else end
		}
	}

	protected class OneLineHighlightDelegate(private val startOffset: Int, private val endOffset: Int, private val lineIterator: LineIterator) : HighlighterIterator{
		private var advance = false
		init {
		    lineIterator.start(startOffset)
		}
		override fun getTextAttributes(): TextAttributes = TextAttributes.ERASE_MARKER

		override fun getStart() = if(startOffset < lineIterator.start) startOffset else lineIterator.start

		override fun getEnd(): Int {
			val end = lineIterator.end.run { if(endOffset - 1 >= getStart()) endOffset - 1 else endOffset }
			return if(end > endOffset) endOffset else end
		}

		override fun getTokenType(): IElementType = IElementType.find(IElementType.FIRST_TOKEN_INDEX)

		override fun advance() {
			advance = true
		}

		override fun retreat() = throw UnsupportedOperationException()

		override fun atEnd() = lineIterator.atEnd() || advance

		override fun getDocument() = throw UnsupportedOperationException()
	}

	companion object{
		fun EditorKind.getMinimap(glancePanel: GlancePanel): BaseMinimap = glancePanel.run {
			val visualFile = editor.virtualFile ?: psiDocumentManager.getPsiFile(glancePanel.editor.document)?.virtualFile
			val isLogFile = visualFile?.run { fileType::class.qualifiedName?.contains("ideolog") } ?: false
			if(this@getMinimap == EditorKind.CONSOLE || visualFile == null ||
				(this@getMinimap == EditorKind.MAIN_EDITOR && CodeGlanceConfigService.getConfig().useFastMinimapForMain)) {
				FastMainMinimap(this, isLogFile)
			}else MainMinimap(this, isLogFile)
		}
	}
}