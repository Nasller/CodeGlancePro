package com.nasller.codeglance.panel

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.Range
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.EditorPanelInjector
import com.nasller.codeglance.concurrent.DirtyLock
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.listener.GlanceListener
import com.nasller.codeglance.listener.HideScrollBarListener
import com.nasller.codeglance.panel.scroll.ScrollBar
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import com.nasller.codeglance.render.Minimap
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.image.BufferedImage
import java.util.function.Function
import javax.swing.JPanel

class GlancePanel(val project: Project, val editor: EditorImpl) : JPanel(), Disposable {
	var originalScrollbarWidth = editor.scrollPane.verticalScrollBar.preferredSize.width
	val config = CodeGlanceConfigService.ConfigInstance.state
	val fileEditorManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
	val myRangeList: MutableList<Pair<Int, Range<Int>>> = ContainerUtil.createLockFreeCopyOnWriteList()
	val scrollState = ScrollState()
	val isDisabled: Boolean
		get() = config.disabled || editor.document.lineCount > config.maxLinesCount
	private val renderLock = DirtyLock()
	val myPopHandler = CustomScrollBarPopup(this)
	val hideScrollBarListener = HideScrollBarListener(this)
	val scrollbar = ScrollBar(this)
	var myVcsPanel: MyVcsPanel? = null
	private var mapRef = MinimapCache { MinimapRef(Minimap(this)) }
	private var buf: BufferedImage? = null

	init {
		Disposer.register(editor.disposable, this)
		Disposer.register(this,GlanceListener(this))
		isOpaque = false
		editor.component.isOpaque = false
		layout = BorderLayout()
		add(scrollbar)
		isVisible = !isDisabled
		refresh()
		editor.putUserData(CURRENT_GLANCE, this)
	}

	fun refresh(refreshImage: Boolean = true, directUpdate: Boolean = false) {
		if (refreshImage) updateImage(directUpdate, updateScroll = true)
		else repaint()
		revalidate()
	}

	fun updateImage(directUpdate: Boolean = false, updateScroll: Boolean = false) =
		if (checkVisible() && renderLock.acquire()) {
			if (directUpdate) updateImgTask(updateScroll)
			else ApplicationManager.getApplication().invokeLater { updateImgTask(updateScroll) }
		} else Unit

	private fun updateImgTask(updateScroll: Boolean = false) {
		try {
			if (updateScroll) updateScrollState()
			mapRef.get(ScaleContext.create(this)).update()
		} finally {
			renderLock.release()
			if (renderLock.dirty) {
				renderLock.clean()
				updateImage()
			}
			repaint()
		}
	}

	fun updateScrollState() = scrollState.run {
		computeDimensions()
		recomputeVisible(editor.scrollingModel.visibleArea)
	}

	fun checkVisible() = !((!config.hoveringToShowScrollBar && !isVisible) || editor.isDisposed)

	fun Graphics2D.paintVcs(rangeOffset: Range<Int>) {
		composite = if (config.hideOriginalScrollBar) srcOver else srcOver0_4
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
			if (it.isThinErrorStripeMark) it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
				val start = editor.offsetToVisualPosition(it.startOffset)
				val end = editor.offsetToVisualPosition(it.endOffset)
				val documentLine = getMyRenderLine(start.line, end.line)
				val sY = start.line * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
				val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
				if (sY >= 0 || eY >= 0) {
					color = this
					if (sY == eY) {
						fillRect(0, sY, width, config.pixelsPerLine)
					} else {
						val notEqual = eY + config.pixelsPerLine != sY
						fillRect(0, sY, width, config.pixelsPerLine)
						if (notEqual) fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
						fillRect(0, eY, width, config.pixelsPerLine)
					}
				}
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	private fun Graphics2D.paintSelection() {
		for ((index, startByte) in editor.selectionModel.blockSelectionStarts.withIndex()) {
			val endByte = editor.selectionModel.blockSelectionEnds[index]
			val start = editor.offsetToVisualPosition(startByte)
			val end = editor.offsetToVisualPosition(endByte)
			val documentLine = getMyRenderLine(start.line, end.line)

			val sX = start.column
			val sY = start.line * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
			val eX = end.column + 1
			val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
			if (sY >= 0 || eY >= 0) {
				setGraphics2DInfo(srcOver, editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
				// Single line is real easy
				if (start.line == end.line) {
					fillRect(sX, sY, eX - sX, config.pixelsPerLine)
				} else {
					// Draw the line leading in
					fillRect(sX, sY, width - sX, config.pixelsPerLine)
					// Then the line at the end
					fillRect(0, eY, eX, config.pixelsPerLine)
					if (eY + config.pixelsPerLine != sY) {
						// And if there is anything in between, fill it in
						fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
					}
				}
			}
		}
	}

	private fun Graphics2D.paintCaretPosition() {
		setGraphics2DInfo(srcOver, editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
		editor.caretModel.allCarets.forEach {
			val documentLine = getMyRenderLine(it.visualPosition.line, it.visualPosition.line)
			val start = it.visualPosition.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
			if (start >= 0) fillRect(0, start, width, config.pixelsPerLine)
		}
	}

	private fun Graphics2D.paintEditorMarkupModel(rangeOffset: Range<Int>) {
		val map by lazy { hashMapOf<String, Int>() }
		editor.markupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
			it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
				val highlightColor = RangeHighlightColor(it, this)
				map.compute(highlightColor.startOffset.toString() + highlightColor.endOffset) { _, layer ->
					if (layer == null || layer < it.layer) {
						drawMarkupLine(highlightColor)
						return@compute it.layer
					}
					return@compute layer
				}
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	private fun Graphics2D.paintEditorFilterMarkupModel(rangeOffset: Range<Int>) {
		editor.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(rangeOffset.from, rangeOffset.to) {
			if (!it.isThinErrorStripeMark && it.layer >= HighlighterLayer.CARET_ROW && it.layer <= HighlighterLayer.SELECTION) {
				it.getErrorStripeMarkColor(editor.colorsScheme)?.apply {
					val highlightColor = RangeHighlightColor(it, this,
                        config.showFullLineHighlight && (config.hideOriginalScrollBar || HighlightInfo.fromRangeHighlighter(it) == null))
					drawMarkupLine(highlightColor)
				}
			}
			return@processRangeHighlightersOverlappingWith true
		}
	}

	private fun Graphics2D.drawMarkupLine(it: RangeHighlightColor) {
		val start = it.startVis
		val end = it.endVis
		val documentLine = getMyRenderLine(start.line, end.line)
		var sX = if (start.column > (width - minGap)) width - minGap else start.column
		val sY = start.line * config.pixelsPerLine + documentLine.first - scrollState.visibleStart
		var eX = if (start.column < (width - minGap)) end.column + 1 else width
		val eY = end.line * config.pixelsPerLine + documentLine.second - scrollState.visibleStart
		if (sY >= 0 || eY >= 0) {
			setGraphics2DInfo(if (it.fullLine && it.fullLineWithActualHighlight) srcOver0_6 else srcOver, it.color)
			val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(it.startOffset)
			if (sY == eY && collapsed == null) {
				if (it.fullLineWithActualHighlight && eX - sX < minGap) {
					eX += minGap - (eX - sX)
					if (eX > width) sX -= eX - width
				}
				drawMarkOneLine(it, sY, sX, eX)
			} else if (collapsed != null) {
				val startVis = editor.offsetToVisualPosition(collapsed.startOffset)
				val endVis = editor.offsetToVisualPosition(collapsed.endOffset)
				drawMarkOneLine(it, sY, startVis.column, endVis.column)
			} else {
				val notEqual = eY + config.pixelsPerLine != sY
				fillRect(if (it.fullLine) 0 else sX, sY, if (it.fullLine) width else width - sX, config.pixelsPerLine)
				if (notEqual) fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
				fillRect(0, eY, if (it.fullLine) width else eX, config.pixelsPerLine)
			}
		}
	}

	private fun Graphics2D.drawMarkOneLine(it: RangeHighlightColor, sY: Int, sX: Int, eX: Int) {
		if (it.fullLine && it.fullLineWithActualHighlight) {
			fillRect(0, sY, width, config.pixelsPerLine)
			setGraphics2DInfo(srcOver, it.color.brighter())
			fillRect(sX, sY, eX - sX, config.pixelsPerLine)
		} else if (it.fullLine) {
			fillRect(0, sY, width, config.pixelsPerLine)
		} else {
			fillRect(sX, sY, eX - sX, config.pixelsPerLine)
		}
	}

	private fun Graphics2D.setGraphics2DInfo(al: AlphaComposite, col: Color?) {
		composite = al
		color = col
	}

	private fun getMyRenderLine(lineStart: Int, lineEnd: Int): Pair<Int, Int> {
		var startAdd = 0
		var endAdd = 0
		myRangeList.forEach {
			if (it.first in (lineStart + 1) until lineEnd) {
				endAdd += it.second.to - it.second.from
			} else if (it.first < lineStart) {
				val i = it.second.to - it.second.from
				startAdd += i
				endAdd += i
			}
		}
		return startAdd to endAdd
	}

	fun getMyRenderVisualLine(y: Int): Int {
		var minus = 0
		for (pair in myRangeList) {
			if (y in pair.second.from..pair.second.to) {
				return pair.first
			} else if (pair.second.to < y) {
				minus += pair.second.to - pair.second.from
			}
		}
		return (y - minus) / config.pixelsPerLine
	}

	fun getVisibleRangeOffset(): Range<Int> {
		var startOffset = 0
		var endOffset = editor.document.textLength
		if (scrollState.visibleStart > 0) {
			val offset = editor.visualLineStartOffset(fitLineToEditor(editor, getMyRenderVisualLine(scrollState.visibleStart))) - 1
			startOffset = if (offset > 0) offset else 0
		}
		if (scrollState.visibleEnd > 0) {
			val offset = EditorUtil.getVisualLineEndOffset(editor, fitLineToEditor(editor, getMyRenderVisualLine(scrollState.visibleEnd))) + 1
			endOffset = if (offset < endOffset) offset else endOffset
		}
		return Range(startOffset, endOffset)
	}

	override fun getPreferredSize():Dimension{
		val calWidth = if (fileEditorManagerEx.isInSplitter) {
			val calWidth = editor.component.width / 12
			if (calWidth < config.width) {
				if (calWidth < 20) 20 else calWidth
			} else config.width
		} else config.width
		return Dimension(calWidth, 0)
	}

	override fun paint(gfx: Graphics) {
		if (renderLock.locked) return paintLast(gfx)
		val img = getDrawImage() ?: return
		if (buf == null || buf?.width!! < width || buf?.height!! < height) {
			buf = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
		}
		val bufGraphics = buf!!.graphics.create() as Graphics2D
		val graphics2D = gfx.create() as Graphics2D
		try {
			bufGraphics.composite = CLEAR
			bufGraphics.fillRect(0, 0, width, height)
			if (editor.document.textLength != 0) {
				bufGraphics.composite = srcOver
				bufGraphics.drawImage(
					img, 0, 0, img.width, scrollState.drawHeight,
					0, scrollState.visibleStart, img.width, scrollState.visibleEnd, null
				)
			}
			graphics2D.paintSomething()
			graphics2D.composite = srcOver0_8
			graphics2D.drawImage(buf, 0, 0, null)
			scrollbar.paint(graphics2D)
		}finally {
			bufGraphics.dispose()
			graphics2D.dispose()
		}
	}

	private fun getDrawImage(): BufferedImage? = mapRef.get(ScaleContext.create(this)).let {
		if (!it.img.isInitialized()) {
			updateImage()
			null
		} else {
			it.img.value
		}
	}

	private fun paintLast(gfx: Graphics) = UIUtil.useSafely(gfx){
		buf?.apply {
			it.composite = srcOver0_8
			it.drawImage(this, 0, 0, width, height, 0, 0, width, height, null)
		}
		it.paintSomething()
		scrollbar.paint(it)
	}

	private fun Graphics2D.paintSomething() {
		val rangeOffset = getVisibleRangeOffset()
		if (!config.hideOriginalScrollBar) paintVcs(rangeOffset)
		if (editor.selectionModel.hasSelection()) paintSelection()
		else paintCaretPosition()
		paintEditorFilterMarkupModel(rangeOffset)
		paintEditorMarkupModel(rangeOffset)
	}

	fun changeOriginScrollBarWidth(control: Boolean = true) {
		if (config.hideOriginalScrollBar && control && (!isDisabled || isVisible)) {
			myVcsPanel?.apply { isVisible = true }
			editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(0, preferredSize.height) }
		} else {
			myVcsPanel?.apply { isVisible = false }
			editor.scrollPane.verticalScrollBar.apply { preferredSize = Dimension(originalScrollbarWidth, preferredSize.height) }
		}
	}

	override fun dispose() {
		editor.putUserData(CURRENT_GLANCE, null)
		editor.component.remove(if (this.parent is EditorPanelInjector.MyPanel) this.parent else this)
		hideScrollBarListener.removeHideScrollBarListener()
		scrollbar.dispose()
		mapRef.clear()
	}

	inner class RangeHighlightColor(val startOffset: Int, val endOffset: Int, val color: Color, val fullLine: Boolean, val fullLineWithActualHighlight: Boolean) {
		constructor(it: RangeHighlighterEx, color: Color) : this(it.startOffset, it.endOffset, color, false, false)
		constructor(it: RangeHighlighterEx, color: Color, fullLine: Boolean) : this(it.startOffset, it.endOffset, color, fullLine, it.targetArea == HighlighterTargetArea.EXACT_RANGE)

		val startVis by lazy { editor.offsetToVisualPosition(startOffset) }
		val endVis by lazy { editor.offsetToVisualPosition(endOffset) }
	}

	companion object {
		const val minGap = 15
		const val minWidth = 50
		const val maxWidth = 250
		val CLEAR: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
		val srcOver0_4: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f)
		val srcOver0_6: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.60f)
		val srcOver0_8: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
		val srcOver: AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
		val CURRENT_GLANCE = Key<GlancePanel>("CURRENT_GLANCE")

		fun fitLineToEditor(editor: EditorImpl, visualLine: Int): Int {
			val lineCount = editor.visibleLineCount
			var shift = 0
			if (visualLine >= lineCount - 1) {
				val sequence = editor.document.charsSequence
				shift = if (sequence.isEmpty()) 0 else if (sequence[sequence.length - 1] == '\n') 1 else 0
			}
			return 0.coerceAtLeast((lineCount - shift).coerceAtMost(visualLine))
		}
	}
}

private class MinimapRef(minimap: Minimap) : SoftReference<Minimap?>(minimap) {
	private var strongRef: Minimap?

	init {
		strongRef = minimap
	}

	override fun get(): Minimap? {
		val minimap = strongRef ?: super.get()
		// drop on first request
		strongRef = null
		return minimap
	}
}

private class MinimapCache(imageProvider: Function<in ScaleContext, MinimapRef>) : ScaleContext.Cache<MinimapRef?>(imageProvider) {
	fun get(ctx: ScaleContext): Minimap {
		val ref = getOrProvide(ctx)
		val image = SoftReference.dereference(ref)
		if (image != null) return image
		clear() // clear to recalculate the image
		return get(ctx) // first recalculated image will be non-null
	}
}