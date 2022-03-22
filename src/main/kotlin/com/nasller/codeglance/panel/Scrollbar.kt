package com.nasller.codeglance.panel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.config.SettingsChangePublisher
import com.nasller.codeglance.render.ScrollState
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.roundToInt

class Scrollbar(textEditor: TextEditor, private val scrollstate : ScrollState, private val panel: AbstractGlancePanel<*>) : JPanel(),Disposable, SettingsChangeListener {
    private val editor = textEditor.editor as EditorEx
    private val defaultCursor = Cursor(Cursor.DEFAULT_CURSOR)

    private var visibleRectAlpha = DEFAULT_ALPHA
        set(value) {
            if (field != value) {
                field = value
                parent.repaint()
            }
        }

    private val config = ConfigInstance.state

    private lateinit var visibleRectColor: Color
    private val vOffset: Int
        get() = scrollstate.viewportStart - scrollstate.visibleStart

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(SettingsChangeListener.TOPIC,this)
        onColorChanged(config.viewportColor)
        val mouseHandler = MouseHandler(textEditor)
        addMouseListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    private fun isInReizeGutter(x: Int): Boolean {
        if (config.locked) {
            return false
        }
        return if (config.isRightAligned)
            x in 0..7
        else
            x in (config.width - 8)..config.width
    }

    private fun isInRect(y: Int): Boolean = y in vOffset..(vOffset + scrollstate.viewportHeight)

    private fun jumpToLineAt(y: Int) {
        val scrollingModel = editor.scrollingModel
        val line = (y + scrollstate.visibleStart) / config.pixelsPerLine
        val offset = scrollstate.viewportHeight / config.pixelsPerLine / 2
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
        g.fillRect(0, vOffset, width, scrollstate.viewportHeight)
    }

    inner class MouseHandler(private val textEditor: TextEditor) : MouseAdapter() {
        private var resizing = false
        private var resizeStart: Int = 0

        private var dragging = false
        private var dragStart: Int = 0
        private var dragStartDelta: Int = 0

        private var widthStart: Int = 0

        override fun mousePressed(e: MouseEvent?) {
            if (e!!.button != MouseEvent.BUTTON1)
                return

            when {
                isInReizeGutter(e.x) -> {
                    resizing = true
                    resizeStart = e.xOnScreen
                    widthStart = config.width
                }
                isInRect(e.y) -> {
                    dragging = true
                    visibleRectAlpha = DRAG_ALPHA
                    dragStart = e.y
                    dragStartDelta = scrollstate.viewportStart - scrollstate.visibleStart
                    // Disable animation when dragging for better experience.
                    editor.scrollingModel.disableAnimation()
                }
                config.jumpOnMouseDown -> jumpToLineAt(e.y)
            }
        }

        override fun mouseDragged(e: MouseEvent?) {
            if (resizing) {
                val newWidth = widthStart + if(config.isRightAligned) resizeStart - e!!.xOnScreen else e!!.xOnScreen - resizeStart
                config.width = newWidth.coerceIn(50, 250)
                panel.refresh()
            } else if (dragging) {
                val delta = (dragStartDelta + (e!!.y - dragStart)).toFloat()
                val newPos = if (scrollstate.documentHeight < scrollstate.visibleHeight)
                    // Full doc fits into minimap, use exact value
                    delta
                else scrollstate.run {
                    // Who says algebra is useless?
                    // delta = newPos - ((newPos / (documentHeight - viewportHeight + 1)) * (documentHeight - visibleHeight + 1))
                    // ...Solve for newPos...
                    delta * (documentHeight - viewportHeight + 1) / (visibleHeight - viewportHeight)
                }
                editor.scrollingModel.scrollVertically((newPos / scrollstate.scale).roundToInt())
            }
        }

        override fun mouseReleased(e: MouseEvent?) {
            if (!config.jumpOnMouseDown && !dragging && !resizing) {
                jumpToLineAt(e!!.y)
            }else if(resizing && !dragging){
                SettingsChangePublisher.onRefreshChanged(config.disabled,textEditor)
            }
            dragging = false
            resizing = false
            updateAlpha(e!!.y)
            editor.scrollingModel.enableAnimation()
        }

        override fun mouseMoved(e: MouseEvent?) {
            cursor = if (isInReizeGutter(e!!.x)) {
                if (config.isRightAligned) Cursor(Cursor.W_RESIZE_CURSOR) else Cursor(Cursor.E_RESIZE_CURSOR)
            } else {
                defaultCursor
            }

            updateAlpha(e.y)
        }

        override fun mouseExited(e: MouseEvent?) {
            if (!dragging)
                visibleRectAlpha = DEFAULT_ALPHA
        }

        override fun mouseWheelMoved(mouseWheelEvent: MouseWheelEvent) {
            editor.contentComponent.dispatchEvent(mouseWheelEvent)
        }
    }

    override fun onColorChanged(newValue: String) {
        visibleRectColor = Color.decode("#$newValue")
    }

    private companion object {
        const val DEFAULT_ALPHA = 0.15f
        const val HOVER_ALPHA = 0.25f
        const val DRAG_ALPHA = 0.35f
    }

    override fun dispose() {
    }
}