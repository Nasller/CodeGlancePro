package com.nasller.codeglance.panel.scroll

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HintHint
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MouseEventAdapter
import com.nasller.codeglance.panel.AbstractGlancePanel
import com.nasller.codeglance.panel.GlancePanel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollBar(
    private val glancePanel: GlancePanel,
) : JPanel(), Disposable {
    var hovering = false
    private val config = glancePanel.config
    private val editor = glancePanel.editor
    private val scrollState = glancePanel.scrollState
    private val myEditorFragmentRenderer = CustomEditorFragmentRenderer(editor)
    private val notReaderMode
        get() = !DocRenderManager.isDocRenderingEnabled(editor)

    private var visibleRectAlpha = DEFAULT_ALPHA
        set(value) {
            if (field != value) {
                field = value
                parent.repaint()
            }
        }

    private var visibleRectColor: Color = Color.decode("#"+config.viewportColor)
    //矩形y轴
    private val vOffset: Int
        get() = scrollState.viewportStart - scrollState.visibleStart

    init {
        val mouseHandler = MouseHandler()
        addMouseListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseListener(glancePanel.myPopHandler)
    }

    private fun isInResizeGutter(x: Int): Boolean {
        if (config.locked || glancePanel.fileEditorManagerEx.isInSplitter) {
            return false
        }
        return x in 0..7
    }

    private fun isInRect(y: Int): Boolean = y in vOffset..(vOffset + scrollState.viewportHeight)

    private fun updateAlpha(y: Int):Boolean {
        return when {
            isInRect(y) -> {
                visibleRectAlpha = HOVER_ALPHA
                cursor = Cursor(Cursor.DEFAULT_CURSOR)
                true
            }
            else -> {
                visibleRectAlpha = DEFAULT_ALPHA
                cursor = if(y < scrollState.drawHeight) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor(Cursor.DEFAULT_CURSOR)
                false
            }
        }
    }

    override fun paint(gfx: Graphics) {
        val g = gfx as Graphics2D
        g.color = visibleRectColor
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibleRectAlpha)
        g.fillRect(0, vOffset, width, scrollState.viewportHeight)
    }

    inner class MouseHandler : MouseAdapter() {
        private var resizing = false
        private var resizeStart: Int = 0

        private var dragging = false
        private var dragStart: Int = 0
        private var dragStartDelta: Int = 0

        private var widthStart: Int = 0

        //视图滚动
        private var myWheelAccumulator = 0
        private var myLastVisualLine = 0
        private val alarm = Alarm(glancePanel)

        override fun mousePressed(e: MouseEvent) {
            if (e.button != MouseEvent.BUTTON1) return
            when {
                isInResizeGutter(e.x) -> {
                    resizing = true
                    resizeStart = e.xOnScreen
                    widthStart = glancePanel.width
                }
                isInRect(e.y) -> dragMove(e.y)
                config.jumpOnMouseDown -> {
                    jumpToLineAt(e.y)
                    editor.scrollingModel.runActionOnScrollingFinished {
                        updateAlpha(e.y)
                        dragMove(e.y)
                    }
                }
            }
        }

        private fun dragMove(y: Int) {
            dragging = true
            visibleRectAlpha = DRAG_ALPHA
            dragStart = y
            dragStartDelta = scrollState.viewportStart - scrollState.visibleStart
        }

        override fun mouseDragged(e: MouseEvent) {
            if (resizing) {
                val newWidth = widthStart + resizeStart - e.xOnScreen
                config.width = newWidth.coerceIn(AbstractGlancePanel.minWidth, AbstractGlancePanel.maxWidth)
                glancePanel.refresh()
            } else if (dragging) {
                val delta = (dragStartDelta + (e.y - dragStart)).toFloat()
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
            }else if(!config.jumpOnMouseDown) showMyEditorPreviewHint(e)
        }

        override fun mouseReleased(e: MouseEvent) {
            if (!config.jumpOnMouseDown && !dragging && !resizing && !e.isPopupTrigger) {
                jumpToLineAt(e.y)
                editor.scrollingModel.runActionOnScrollingFinished { updateAlpha(e.y) }
            }else updateAlpha(e.y)
            dragging = false
            resizing = false
            hideScrollBar(e)
        }

        override fun mouseMoved(e: MouseEvent) {
            hovering = true
            val isInRect = updateAlpha(e.y)
            if (isInResizeGutter(e.x)) {
                cursor = Cursor(Cursor.W_RESIZE_CURSOR)
            } else if(!isInRect && !resizing && !dragging && showMyEditorPreviewHint(e)){
                return
            }
            hideMyEditorPreviewHint()
        }

        private fun showMyEditorPreviewHint(e: MouseEvent): Boolean {
            if (config.showEditorToolTip && notReaderMode && e.x > 10 && e.y < scrollState.drawHeight) {
                if (myEditorFragmentRenderer.getEditorPreviewHint() == null) {
                    alarm.cancelAllRequests()
                    alarm.addRequest({
                        if (myEditorFragmentRenderer.getEditorPreviewHint() == null) showToolTipByMouseMove(e)
                    }, 400)
                } else showToolTipByMouseMove(e)
                return true
            }
            return false
        }

        override fun mouseExited(e: MouseEvent) {
            if (!dragging){
                visibleRectAlpha = DEFAULT_ALPHA
            }
            hideMyEditorPreviewHint()
            hideScrollBar(e)
        }

        override fun mouseWheelMoved(e: MouseWheelEvent) {
            if (myEditorFragmentRenderer.getEditorPreviewHint() == null) {
                // process wheel event by the parent scroll pane if no code lens
                MouseEventAdapter.redispatch(e, e.component.parent)
                return
            }
            val units: Int = e.unitsToScroll
            if (units == 0) return
            if (myLastVisualLine < editor.visibleLineCount - 1 && units > 0 || myLastVisualLine > 0 && units < 0) {
                myWheelAccumulator += units
            }
            showToolTipByMouseMove(e)
        }

        private fun hideScrollBar(e: MouseEvent){
            if(!dragging && !resizing && !e.isPopupTrigger){
                hovering = false
                glancePanel.hideScrollBarListener.hideGlanceRequest()
            }
        }

        private fun jumpToLineAt(y: Int) {
            val visualLine = (y + scrollState.visibleStart) / config.pixelsPerLine
            val renderLine = editor.visualToLogicalPosition(VisualPosition(visualLine, 0)).line.run{
                glancePanel.getDocumentRenderLine(this, this)
            }
            val line = fitLineToEditor(editor, visualLine - renderLine.first)
            editor.caretModel.moveToVisualPosition(VisualPosition(line,0))
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            hideMyEditorPreviewHint()
        }

        private fun showToolTipByMouseMove(e: MouseEvent) {
            val y = e.y + myWheelAccumulator
            val visualLine = fitLineToEditor(editor, (y + scrollState.visibleStart) / config.pixelsPerLine)
            myLastVisualLine = visualLine
            val point = SwingUtilities.convertPoint(
                this@ScrollBar, 0,
                if (y > 0 && y < scrollState.drawHeight) y else if (y <= 0) 0 else scrollState.drawHeight, editor.scrollPane.verticalScrollBar
            )
            val me = MouseEvent(
                editor.scrollPane.verticalScrollBar, e.id, e.`when`, e.modifiersEx, 1,
                point.y, e.clickCount, e.isPopupTrigger
            )
            val highlighters = mutableListOf<RangeHighlighterEx>()
            collectRangeHighlighters(editor.markupModel, visualLine, highlighters)
            collectRangeHighlighters(editor.filteredDocumentMarkupModel, visualLine, highlighters)
            myEditorFragmentRenderer.show(visualLine, highlighters, createHint(me))
        }

        private fun hideMyEditorPreviewHint() {
            alarm.cancelAllRequests()
            myEditorFragmentRenderer.hideHint()
            myWheelAccumulator = 0
            myLastVisualLine = 0
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

        private fun getOffset(visualLine: Int, startLine: Boolean): Int {
            return editor.visualPositionToOffset(VisualPosition(visualLine, if (startLine) 0 else Int.MAX_VALUE))
        }
    }

    override fun dispose() {
        myEditorFragmentRenderer.clearHint()
    }

    companion object {
        private const val DEFAULT_ALPHA = 0.15f
        private const val HOVER_ALPHA = 0.25f
        private const val DRAG_ALPHA = 0.35f
        val PREVIEW_LINES = max(2, min(25, Integer.getInteger("preview.lines", 5)))

        fun fitLineToEditor(editor: EditorImpl, visualLine: Int): Int {
            val lineCount = editor.visibleLineCount
            var shift = 0
            if (visualLine >= lineCount - 1) {
                val sequence = editor.document.charsSequence
                shift = if (sequence.isEmpty()) 0 else if (sequence[sequence.length - 1] == '\n') 1 else 0
            }
            return 0.coerceAtLeast((lineCount - shift).coerceAtMost(visualLine))
        }

        private fun createHint(me: MouseEvent): HintHint {
            return HintHint(me)
                .setAwtTooltip(true)
                .setPreferredPosition(Balloon.Position.atLeft)
                .setBorderInsets(JBUI.insets(CustomEditorFragmentRenderer.EDITOR_FRAGMENT_POPUP_BORDER))
                .setShowImmediately(true)
                .setAnimationEnabled(false)
        }
    }
}