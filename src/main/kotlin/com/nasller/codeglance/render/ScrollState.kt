package com.nasller.codeglance.render

import com.nasller.codeglance.panel.GlancePanel
import java.awt.Rectangle
import kotlin.math.min

class ScrollState {
    var scale:Float = 0F
        private set
    var documentHeight: Int = 0
        private set
    var visibleStart: Int = 0
        private set
    var visibleEnd: Int = 0
        private set
    var visibleHeight: Int = 0
        private set
    //当前图片高度
    var drawHeight: Int = 0
        private set
    var viewportStart: Int = 0
        private set
    //矩形框高度
    var viewportHeight: Int = 0
        private set

    fun GlancePanel.computeDimensions() {
        val oldScale = scale
        scale = config.pixelsPerLine.toFloat() / editor.lineHeight
        documentHeight = (editor.contentComponent.height * scale).toInt()
        if(oldScale > 0 && oldScale != scale) refreshDataAndImage()
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