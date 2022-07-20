package com.nasller.codeglance.render

import com.nasller.codeglance.panel.AbstractGlancePanel
import java.awt.Rectangle
import kotlin.math.min

data class ScrollState(
    var scale:Float = 0F,
    var documentHeight: Int = 0,
    var visibleStart: Int = 0,
    var visibleEnd: Int = 0,
    var visibleHeight: Int = 0,
    var drawHeight: Int = 0,//当前图片高度
    var viewportStart: Int = 0,
    var viewportRealStart: Int = 0,
    var viewportHeight: Int = 0//矩形框高度
) {
    fun AbstractGlancePanel.computeDimensions() {
        scale = config.pixelsPerLine.toFloat() / editor.lineHeight
        documentHeight = (editor.contentComponent.height * scale).toInt()
    }

    fun recomputeVisible(visibleArea: Rectangle) {
        visibleHeight = visibleArea.height
        drawHeight = min(visibleHeight, documentHeight)

        viewportStart = (visibleArea.y * scale).toInt()
        viewportHeight = (visibleArea.height * scale).toInt()

        visibleStart = ((viewportStart.toFloat() / (documentHeight - viewportHeight + 1)) * (documentHeight - visibleHeight + 1)).toInt().coerceAtLeast(0)
        visibleEnd = visibleStart + drawHeight
    }
}