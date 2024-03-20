package com.nasller.codeglance.panel.scroll

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.diff.EditorDiffViewer
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.ColorUtil
import com.intellij.ui.HintHint
import com.intellij.util.Alarm
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MouseEventAdapter
import com.nasller.codeglance.CURRENT_EDITOR_DIFF_VIEW
import com.nasller.codeglance.config.CodeGlanceConfig.Companion.setWidth
import com.nasller.codeglance.config.SettingsChangePublisher
import com.nasller.codeglance.config.enums.ClickTypeEnum
import com.nasller.codeglance.config.enums.MouseJumpEnum
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.GlancePanel.Companion.fitLineToEditor
import com.nasller.codeglance.util.Util.alignedToY
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollBar(private val glancePanel: GlancePanel) : MouseAdapter() {
	private val config
		get() = glancePanel.config
	private val editor
		get() = glancePanel.editor
	private val scrollState
		get() = glancePanel.scrollState
	private val alarm = Alarm(glancePanel)
	private val myEditorFragmentRenderer = CustomEditorFragmentRenderer(editor)
	private var visibleRectAlpha = DEFAULT_ALPHA
		set(value) {
			if (field != value) {
				field = value
				glancePanel.repaint()
			}
		}
	private var hovering = false
	//矩形y轴
	private val vOffset: Int
		get() = scrollState.viewportStart - scrollState.visibleStart
	//视图滚动
	private var myWheelAccumulator = 0
	private var myLastVisualLine = 0
	//宽带调整鼠标事件
	private var resizing = false
	private var resizeStart: Int = 0
	private var widthStart: Int = 0
	//拖拽鼠标事件
	private var dragging = false
	private var dragStart: Int = 0
	private var dragStartDelta: Int = 0

	init {
		glancePanel.addMouseListener(this)
		glancePanel.addMouseWheelListener(this)
		glancePanel.addMouseMotionListener(this)
		glancePanel.addMouseListener(glancePanel.myPopHandler)
	}

	fun paint(gfx: Graphics2D) {
		gfx.color = ColorUtil.fromHex(config.viewportColor)
		gfx.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibleRectAlpha)
		val gfxConfig = GraphicsUtil.setupAAPainting(gfx)
		if(scrollState.viewportHeight > MIN_VIEWPORT_HEIGHT) {
			gfx.fillRoundRect(0, vOffset, glancePanel.width, scrollState.viewportHeight,5, 5)
		}else {
			gfx.fillRect(0, vOffset, glancePanel.width, scrollState.viewportHeight)
		}
		getBorderShape(vOffset, glancePanel.width, scrollState.viewportHeight, config.viewportBorderThickness)?.let {
			gfx.composite = GlancePanel.srcOver
			gfx.color = ColorUtil.fromHex(config.viewportBorderColor)
			gfx.fill(it)
		}
		gfxConfig.restore()
	}

	fun clear() = myEditorFragmentRenderer.clearHint()

	override fun mouseEntered(e: MouseEvent) {
		hovering = true
	}

	override fun mousePressed(e: MouseEvent) {
		if (e.button != MouseEvent.BUTTON1) return
		val alignedToY = e.y.alignedToY(glancePanel)
		when {
			isInResizeGutter(e.x) -> {
				resizing = true
				resizeStart = e.xOnScreen
				widthStart = glancePanel.width
			}
			isInRect(alignedToY) || MouseJumpEnum.NONE == config.jumpOnMouseDown -> dragMove(alignedToY)
			MouseJumpEnum.MOUSE_DOWN == config.jumpOnMouseDown -> jumpToLineAt(e.x, e.y) {
				visibleRectAlpha = DEFAULT_ALPHA
				glancePanel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
				dragMove(alignedToY)
			}
		}
	}

	override fun mouseDragged(e: MouseEvent) {
		if (resizing) {
			val newWidth = if(editor.getUserData(GlancePanel.CURRENT_GLANCE_PLACE_INDEX) == GlancePanel.PlaceIndex.Left)
				widthStart + e.xOnScreen - resizeStart
			else widthStart + resizeStart - e.xOnScreen
			editor.editorKind.setWidth(newWidth.coerceIn(GlancePanel.MIN_WIDTH, GlancePanel.MAX_WIDTH))
			resizeGlancePanel(false)
		} else if (dragging) {
			val delta = (dragStartDelta + (e.y.alignedToY(glancePanel) - dragStart)).toFloat()
			val newPos = if (scrollState.documentHeight < scrollState.visibleHeight)
			// Full doc fits into minimap, use exact value
				delta
			else scrollState.run {
				// Who says algebra is useless?
				// delta = newPos - ((newPos / (documentHeight - viewportHeight + 1)) * (documentHeight - visibleHeight + 1))
				// ...Solve for newPos...
				delta * (documentHeight - viewportHeight + 1) / (visibleHeight - viewportHeight)
			}
			editor.scrollPane.verticalScrollBar.value = (newPos / scrollState.scale).roundToInt()
		} else if (MouseJumpEnum.MOUSE_UP == config.jumpOnMouseDown) showMyEditorPreviewHint(e)
	}

	override fun mouseReleased(e: MouseEvent) {
		val action = {
			resizeGlancePanel(true)
			updateAlpha(e.y.alignedToY(glancePanel))
			dragging = false
			resizing = false
			hoveringOverAndHideScrollBar(e)
		}
		if (MouseJumpEnum.MOUSE_UP == config.jumpOnMouseDown && !dragging && !resizing && !e.isPopupTrigger) {
			jumpToLineAt(e, action)
		}else {
			editor.scrollingModel.runActionOnScrollingFinished(action)
		}
	}

	override fun mouseMoved(e: MouseEvent) {
		val isInRect = updateAlpha(e.y.alignedToY(glancePanel))
		if (isInResizeGutter(e.x)) {
			glancePanel.cursor = Cursor(Cursor.W_RESIZE_CURSOR)
		} else if (!isInRect && !resizing && !dragging && showMyEditorPreviewHint(e)) {
			return
		}
		hideMyEditorPreviewHint()
	}

	override fun mouseExited(e: MouseEvent) {
		hovering = false
		if (!dragging) visibleRectAlpha = DEFAULT_ALPHA
		hideMyEditorPreviewHint()
		hoveringOverAndHideScrollBar(e)
	}

	override fun mouseWheelMoved(e: MouseWheelEvent) {
		val hasEditorPreviewHint = myEditorFragmentRenderer.getEditorPreviewHint() != null
		if (config.mouseWheelMoveEditorToolTip && hasEditorPreviewHint){
			val units = e.unitsToScroll
			if (units == 0) return
			if (myLastVisualLine < editor.visibleLineCount - 1 && units > 0 || myLastVisualLine > 0 && units < 0) {
				myWheelAccumulator += units
			}
			showToolTipByMouseMove(e)
		} else {
			if(hasEditorPreviewHint) hideMyEditorPreviewHint()
			MouseEventAdapter.redispatch(e,editor.contentComponent)
		}
	}

	private fun resizeGlancePanel(refreshImage: Boolean) {
		if(!resizing) return
		val action = {it: GlancePanel-> if(refreshImage) SettingsChangePublisher.refreshDataAndImage() else it.refresh() }
		val diffViewer = editor.getUserData(CURRENT_EDITOR_DIFF_VIEW)
		if (diffViewer != null) {
			if (diffViewer is EditorDiffViewer) {
				diffViewer.editors.mapNotNull { it.getUserData(GlancePanel.CURRENT_GLANCE) }.forEach(action)
			}
		} else {
			action(glancePanel)
		}
	}


	private fun dragMove(y: Int) {
		dragging = true
		visibleRectAlpha = DRAG_ALPHA
		dragStart = y
		dragStartDelta = vOffset
	}

	private fun showMyEditorPreviewHint(e: MouseEvent): Boolean {
		return if(config.showEditorToolTip && e.x > 10 && e.y < scrollState.drawHeight) {
			if (myEditorFragmentRenderer.getEditorPreviewHint() == null) {
				alarm.cancelAllRequests()
				alarm.addRequest({
					if (myEditorFragmentRenderer.getEditorPreviewHint() == null) showToolTipByMouseMove(e)
				}, 400)
			} else showToolTipByMouseMove(e)
			true
		}else false
	}

	private fun showToolTipByMouseMove(e: MouseEvent) {
		val y = e.y.alignedToY(glancePanel) + myWheelAccumulator
		val visualLine = fitLineToEditor(editor, glancePanel.getMyRenderVisualLine(y + scrollState.visibleStart))
		myLastVisualLine = visualLine
		val point = SwingUtilities.convertPoint(glancePanel, 0, if (e.y > 0 && e.y < scrollState.drawHeight) e.y else if (e.y <= 0) 0 else scrollState.drawHeight,
			editor.scrollPane.verticalScrollBar)
		val highlighters = mutableListOf<RangeHighlighterEx>()
		collectRangeHighlighters(editor.markupModel, visualLine, highlighters)
		collectRangeHighlighters(editor.filteredDocumentMarkupModel, visualLine, highlighters)
		myEditorFragmentRenderer.show(visualLine, highlighters, createHint(editor.scrollPane.verticalScrollBar, Point(1, point.y)))
	}

	private fun collectRangeHighlighters(markupModel: MarkupModelEx, visualLine: Int, highlighters: MutableCollection<in RangeHighlighterEx>) {
		val startOffset: Int = getOffset(fitLineToEditor(editor, visualLine - PREVIEW_LINES), true)
		val endOffset: Int = getOffset(fitLineToEditor(editor, visualLine + PREVIEW_LINES), false)
		markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset) { highlighter ->
			val tooltip = highlighter.errorStripeTooltip
			if (tooltip != null && !(tooltip is HighlightInfo && tooltip.type === HighlightInfoType.TODO) &&
				highlighter.startOffset < endOffset && highlighter.endOffset > startOffset &&
				highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null) {
				highlighters.add(highlighter)
			}
			true
		}
	}

	private fun getOffset(visualLine: Int, startLine: Boolean): Int =
		editor.visualPositionToOffset(VisualPosition(visualLine, if (startLine) 0 else Int.MAX_VALUE))

	private fun hideMyEditorPreviewHint() {
		alarm.cancelAllRequests()
		myEditorFragmentRenderer.hideHint()
		myWheelAccumulator = 0
		myLastVisualLine = 0
	}

	private fun isInResizeGutter(x: Int): Boolean =
		if (config.locked || config.hoveringToShowScrollBar || glancePanel.isInSplitter()) false else {
				if(editor.getUserData(GlancePanel.CURRENT_GLANCE_PLACE_INDEX) == GlancePanel.PlaceIndex.Left)
					x in glancePanel.width - 7 .. glancePanel.width
				else x in 0..7
		}

	private fun isInRect(y: Int): Boolean = y in vOffset..(vOffset + scrollState.viewportHeight)

	private fun updateAlpha(y: Int): Boolean {
		return when {
			isInRect(y) -> {
				visibleRectAlpha = HOVER_ALPHA
				glancePanel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
				true
			}
			else -> {
				visibleRectAlpha = DEFAULT_ALPHA
				glancePanel.cursor = if (MouseJumpEnum.NONE != config.jumpOnMouseDown && y < scrollState.drawHeight) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
				false
			}
		}
	}

	private fun hoveringOverAndHideScrollBar(e: MouseEvent) {
		if (!e.isPopupTrigger) glancePanel.hideScrollBarListener.hideGlanceRequest()
	}

	private fun jumpToLineAt(e: MouseEvent, action: () -> Unit) {
		hideMyEditorPreviewHint()
		val visualLine = if(config.clickType == ClickTypeEnum.CODE_POSITION){
			fitLineToEditor(editor, glancePanel.getMyRenderVisualLine(e.y.alignedToY(glancePanel) + scrollState.visibleStart))
		}else{
			if(scrollState.drawHeight == scrollState.visibleHeight){
				editor.yToVisualLine((e.y / scrollState.visibleHeight.toFloat() * editor.contentComponent.height).roundToInt())
			}else{
				fitLineToEditor(editor, glancePanel.getMyRenderVisualLine(e.y + scrollState.visibleStart))
			}
		}
		val visualPosition = VisualPosition(visualLine, e.x)
		if(e.isShiftDown){
			editor.selectionModel.setSelection(editor.caretModel.offset, editor.visualPositionToOffset(visualPosition))
		}
		if(config.moveOnly.not()){
			editor.caretModel.moveToVisualPosition(visualPosition)
			editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
		}else {
			editor.scrollingModel.scrollTo(editor.visualToLogicalPosition(visualPosition), ScrollType.CENTER)
		}
		editor.scrollingModel.runActionOnScrollingFinished(action)
	}

	fun isNotHoverScrollBar() = !hovering && !dragging  && !resizing

	companion object {
		private const val DEFAULT_ALPHA = 0.15f
		private const val HOVER_ALPHA = 0.25f
		private const val DRAG_ALPHA = 0.35f
		private const val MIN_VIEWPORT_HEIGHT = 20
		val PREVIEW_LINES = max(2, min(25, Integer.getInteger("preview.lines", 5)))

		private fun createHint(component: Component, point: Point): HintHint = HintHint(component, point)
			.setAwtTooltip(true)
			.setPreferredPosition(Balloon.Position.atLeft)
			.setBorderInsets(JBUI.insets(CustomEditorFragmentRenderer.EDITOR_FRAGMENT_POPUP_BORDER))
			.setShowImmediately(true)
			.setAnimationEnabled(false)
			.setStatus(HintHint.Status.Info)

		private fun getBorderShape(y: Int, width: Int, height: Int, thickness: Int): Shape? {
			if (width <= 0 || height <= 0 || thickness <= 0) return null
			val thicknessSize = if(height > MIN_VIEWPORT_HEIGHT) thickness else 1
			val outer = if(height > MIN_VIEWPORT_HEIGHT) RoundRectangle2D.Float(0f, y.toFloat(), width.toFloat(), height.toFloat(), 5f, 5f)
			else Rectangle2D.Float(0f, y.toFloat(), width.toFloat(), height.toFloat())
			val doubleThickness = 2 * thicknessSize.toFloat()
			if (width <= doubleThickness || height <= doubleThickness) return outer
			val inner = Rectangle2D.Float(0f + thicknessSize.toFloat(), y + thicknessSize.toFloat(), width - doubleThickness, height - doubleThickness)
			val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
			path.append(outer, false)
			path.append(inner, false)
			return path
		}
	}
}