package com.nasller.codeglance.render

import com.nasller.codeglance.config.enums.EditorSizeEnum
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollState : Cloneable{
    var pixelsPerLine = 0.0
        private set
    var scale = Double.NaN
        private set
    var documentHeight = 0
        private set
    var visibleStart = 0
        private set
    var visibleEnd = 0
        private set
    var visibleHeight = 0
        private set
    //当前图片高度
    var drawHeight = 0
        private set
    var viewportStart = 0
        private set
    //矩形框高度
    var viewportHeight = 0
        private set
    private var initialized = false

    fun GlancePanel.computeDimensions(visibleArea: Rectangle, visibleChange: Boolean): Boolean {
        val lineHeight = editor.lineHeight
        val contentHeight = editor.contentComponent.height
        val newScale = config.pixelsPerLine.toDouble() / lineHeight
        val curDocumentHeight = (contentHeight * newScale).roundToInt()
        if(config.editorSize == EditorSizeEnum.Fit && curDocumentHeight > visibleArea.height) {
            if(visibleArea.height < 1 && initialized) {
                return true
            }
            val oldDocumentHeight = documentHeight.apply { documentHeight = visibleArea.height }
            scale = documentHeight.toDouble() / contentHeight
            pixelsPerLine = scale * lineHeight
            if((oldDocumentHeight > 0 || !initialized) && oldDocumentHeight != documentHeight) {
                val oldInitialized = initialized.apply { initialized = true }
                if(visibleChange && documentHeight > 0 && pixelsPerLine > 0) {
                    if(oldInitialized) {
                        refreshImage()
                    }else {
                        refreshDataAndImage()
                    }
                    return false
                }
            }
        }else {
            pixelsPerLine = config.pixelsPerLine.toDouble()
            documentHeight = curDocumentHeight
            val oldScale = scale.apply { scale = newScale }
            if(visibleChange && !oldScale.isNaN() && oldScale != scale) {
                if(initialized.apply { initialized = true }) {
                    refreshImage()
                }else {
                    refreshDataAndImage()
                }
                return false
            }
        }
        return true
    }

    fun recomputeVisible(visibleArea: Rectangle, pixScale: Double = 1.0) {
        visibleHeight = (visibleArea.height / pixScale).toInt().coerceAtLeast(0)
        drawHeight = min(visibleHeight, documentHeight).coerceAtLeast(0)

        // 视口矩形必须能完整落在当前可绘制窗口内，否则 HiDPI 取整后会在底部出现裁切。
        val maxDrawableViewportHeight = min(documentHeight, drawHeight).coerceAtLeast(0)
        viewportHeight = (visibleArea.height * scale).roundToInt().coerceIn(0, maxDrawableViewportHeight)
        val maxViewportStart = (documentHeight - viewportHeight).coerceAtLeast(0)
        viewportStart = (visibleArea.y * scale).roundToInt().coerceIn(0, maxViewportStart)

        if (drawHeight !in 1..<documentHeight || viewportHeight <= 0 || maxViewportStart == 0) {
            visibleStart = 0
            visibleEnd = drawHeight
            return
        }

        val maxVisibleStart = (documentHeight - drawHeight).coerceAtLeast(0)
        val preferredVisibleStart = (viewportStart.toDouble() / maxViewportStart * maxVisibleStart).roundToInt()
        val minVisibleStart = (viewportStart + viewportHeight - drawHeight).coerceAtLeast(0)
        val maxVisibleStartForViewport = min(viewportStart, maxVisibleStart)

        visibleStart = preferredVisibleStart.coerceIn(min(minVisibleStart, maxVisibleStartForViewport), maxVisibleStartForViewport)
        visibleEnd = (visibleStart + drawHeight).coerceAtMost(documentHeight)
    }

    fun getRenderHeight() = max(1.0, pixelsPerLine).toInt()

    public override fun clone(): ScrollState {
        return super.clone() as ScrollState
    }
}