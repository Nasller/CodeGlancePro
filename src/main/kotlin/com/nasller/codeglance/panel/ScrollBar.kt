package com.nasller.codeglance.panel

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HintHint
import com.intellij.util.ui.JBUI
import com.nasller.codeglance.CodeGlancePlugin.Companion.DocRenderEnabled
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangePublisher
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollBar(textEditor: TextEditor, private val scrollState : ScrollState, private val panel: AbstractGlancePanel<*>) : JPanel() {
    private val editor = textEditor.editor as EditorImpl
    private val defaultCursor = Cursor(Cursor.DEFAULT_CURSOR)

    private var visibleRectAlpha = DEFAULT_ALPHA
        set(value) {
            if (field != value) {
                field = value
                parent.repaint()
            }
        }

    private val config = ConfigInstance.state

    private var visibleRectColor: Color = Color.decode("#"+config.viewportColor)
    //矩形y轴
    private val vOffset: Int
        get() = scrollState.viewportStart - scrollState.visibleStart

    init {
        val mouseHandler = MouseHandler(textEditor)
        addMouseListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    private fun isInResizeGutter(x: Int): Boolean {
        if (config.locked) {
            return false
        }
        return if (config.isRightAligned)
            x in 0..7
        else
            x in (config.width - 8)..config.width
    }

    private fun isInRect(y: Int): Boolean = y in vOffset..(vOffset + scrollState.viewportHeight)

    private fun jumpToLineAt(y: Int) {
        val scrollingModel = editor.scrollingModel
        val line = (y + scrollState.visibleStart) / config.pixelsPerLine
        val offset = scrollState.viewportHeight / config.pixelsPerLine / 2
        scrollingModel.scrollVertically(max(0, line - offset) * editor.lineHeight)
    }

    private fun updateAlpha(y: Int) {
        visibleRectAlpha = when {
            isInRect(y) -> HOVER_ALPHA
            else -> DEFAULT_ALPHA
        }
    }

    override fun paint(gfx: Graphics?) {
        val g = gfx as Graphics2D
        g.color = visibleRectColor
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibleRectAlpha)
        g.fillRect(0, vOffset, width, scrollState.viewportHeight)
    }

    inner class MouseHandler(private val textEditor: TextEditor) : MouseAdapter() {
        private val editor = textEditor.editor as EditorImpl
        private var resizing = false
        private var resizeStart: Int = 0

        private var dragging = false
        private var dragStart: Int = 0
        private var dragStartDelta: Int = 0

        private var widthStart: Int = 0

        //视图滚动
        private var viewing = false
        private var myWheelAccumulator = 0
        private var myLastVisualLine = 0

        override fun mousePressed(e: MouseEvent) {
            if (e.button != MouseEvent.BUTTON1)
                return

            when {
                isInResizeGutter(e.x) -> {
                    resizing = true
                    resizeStart = e.xOnScreen
                    widthStart = config.width
                }
                isInRect(e.y) -> {
                    dragging = true
                    visibleRectAlpha = DRAG_ALPHA
                    dragStart = e.y
                    dragStartDelta = scrollState.viewportStart - scrollState.visibleStart
                    // Disable animation when dragging for better experience.
                    editor.scrollingModel.disableAnimation()
                }
                config.jumpOnMouseDown -> jumpToLineAt(e.y)
            }
        }

        override fun mouseDragged(e: MouseEvent) {
            if (resizing) {
                val newWidth = widthStart + if(config.isRightAligned) resizeStart - e.xOnScreen else e.xOnScreen - resizeStart
                config.width = newWidth.coerceIn(50, 250)
                panel.refresh()
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
                editor.scrollingModel.scrollVertically((newPos / scrollState.scale).roundToInt())
            }
        }

        override fun mouseReleased(e: MouseEvent) {
            if (!config.jumpOnMouseDown && !dragging && !resizing) {
                jumpToLineAt(e.y)
            }else if(resizing && !dragging){
                SettingsChangePublisher.onRefreshChanged(config.disabled,textEditor)
            }
            dragging = false
            resizing = false
            updateAlpha(e.y)
            editor.scrollingModel.enableAnimation()
        }

        override fun mouseMoved(e: MouseEvent) {
            updateAlpha(e.y)
            if (isInResizeGutter(e.x)) {
                cursor = if (config.isRightAligned) Cursor(Cursor.W_RESIZE_CURSOR) else Cursor(Cursor.E_RESIZE_CURSOR)
            } else {
                cursor = defaultCursor
                val enabled = (if(DocRenderEnabled != null){
                    !(editor.getUserData(DocRenderEnabled)?:false)
                }else true) || textEditor.file?.isWritable?:false
                if (!resizing && !dragging && !isInRect(e.y) && enabled && e.y < scrollState.drawHeight) {
                    showToolTipByMouseMove(e)
                    return
                }
            }
            hideMyEditorPreviewHint()
        }

        override fun mouseExited(e: MouseEvent) {
            hideMyEditorPreviewHint()
            if (!dragging)
                visibleRectAlpha = DEFAULT_ALPHA
        }

        override fun mouseWheelMoved(e: MouseWheelEvent) {
            if(viewing){
                val units: Int = e.unitsToScroll
                if (units == 0) return
                if (myLastVisualLine < editor.visibleLineCount - 1 && units > 0 || myLastVisualLine > 0 && units < 0) {
                    myWheelAccumulator += units
                }
                showToolTipByMouseMove(e)
            }else{
                editor.contentComponent.dispatchEvent(e)
            }
        }

        private fun showToolTipByMouseMove(e: MouseEvent){
            if(editorFragmentRendererShow != null && myEditorFragmentRenderer != null){
                val y = e.y + myWheelAccumulator
                val visualLine = fitLineToEditor(editor,((y + scrollState.visibleStart)/ config.pixelsPerLine))
                myLastVisualLine = visualLine
                val point = SwingUtilities.convertPoint(this@ScrollBar, 0,
                    if(y > 0 && y < scrollState.drawHeight) y else if(y <= 0) 0 else scrollState.drawHeight
                    , editor.scrollPane.verticalScrollBar)
                val me = MouseEvent(editor.scrollPane.verticalScrollBar, e.id, e.`when`, e.modifiersEx, 1,
                    point.y, e.clickCount, e.isPopupTrigger)
                val highlighters = mutableListOf<RangeHighlighterEx>()
                collectRangeHighlighters(editor.markupModel, visualLine, highlighters)
                collectRangeHighlighters(editor.filteredDocumentMarkupModel, visualLine, highlighters)
                editorFragmentRendererShow.invoke(myEditorFragmentRenderer.get(editor.markupModel),visualLine,
                    highlighters, me.isAltDown, createHint(me))
                viewing = true
            }
        }

        private fun hideMyEditorPreviewHint() {
            viewing = false
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

        private fun createHint(me: MouseEvent): HintHint? {
            return HintHint(me)
                .setAwtTooltip(true)
                .setPreferredPosition(Balloon.Position.atLeft)
                .setBorderInsets(JBUI.insets(1))
                .setShowImmediately(true)
                .setAnimationEnabled(false)
        }
    }

    private companion object {
        const val DEFAULT_ALPHA = 0.15f
        const val HOVER_ALPHA = 0.25f
        const val DRAG_ALPHA = 0.35f
        val PREVIEW_LINES = max(2, min(25, Integer.getInteger("preview.lines", 5)))

        private val editorFragmentRendererShow: Method? = try {
                val clazz = Class.forName("com.intellij.openapi.editor.impl.EditorFragmentRenderer")
                val method = clazz.getMethod("show",Int::class.java,Collection::class.java,Boolean::class.java, HintHint::class.java)
                method.isAccessible = true
                method
            }catch (e:Exception){
                null
            }
        private val myEditorFragmentRenderer: Field? = try {
            val implClass = EditorMarkupModelImpl::class.java
            val field = implClass.getDeclaredField("myEditorFragmentRenderer")
            field.isAccessible = true
            field
        }catch (e:Exception){
            null
        }

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