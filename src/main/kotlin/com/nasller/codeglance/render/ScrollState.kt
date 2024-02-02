package com.nasller.codeglance.render

import com.nasller.codeglance.config.enums.EditorSizeEnum
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Rectangle
import kotlin.math.min

class ScrollState {
    var pixelsPerLine: Double = 0.0
        private set
    var scale: Double = 0.0
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

    fun GlancePanel.computeDimensions(visibleArea: Rectangle): Boolean {
        val lineHeight = editor.lineHeight
        val contentHeight = editor.contentComponent.height
        val newScale = config.pixelsPerLine.toDouble() / lineHeight
        val curDocumentHeight = (contentHeight * newScale).toInt()
        if(config.editorSize == EditorSizeEnum.Fit && curDocumentHeight > visibleArea.height && visibleArea.height > 0) {
            val oldDocumentHeight = documentHeight.apply { documentHeight = visibleArea.height }
            scale = documentHeight.toDouble() / contentHeight
            pixelsPerLine = scale * lineHeight
            if(oldDocumentHeight > 0 && oldDocumentHeight != documentHeight) {
                refreshDataAndImage()
                return false
            }
        }else {
            pixelsPerLine = config.pixelsPerLine.toDouble()
            documentHeight = curDocumentHeight
            val oldScale = scale.apply { scale = newScale }
            if(oldScale > 0 && oldScale != scale) {
                refreshDataAndImage()
                return false
            }
        }
        return true
    }

    fun recomputeVisible(visibleArea: Rectangle) {
        visibleHeight = visibleArea.height
        drawHeight = min(visibleHeight, documentHeight)

        viewportStart = (visibleArea.y * scale).toInt()
        viewportHeight = (visibleArea.height * scale).toInt()

        visibleStart = ((viewportStart.toFloat() / (documentHeight - viewportHeight + 1)) * (documentHeight - visibleHeight + 1)).toInt().coerceAtLeast(0)
        visibleEnd = visibleStart + drawHeight
    }

    fun getRenderHeight(): Int {
        return if(pixelsPerLine < 1) 1 else pixelsPerLine.toInt()
    }
}