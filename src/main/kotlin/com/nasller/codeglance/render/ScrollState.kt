package com.nasller.codeglance.render

import com.intellij.openapi.editor.Editor
import com.nasller.codeglance.config.Config
import java.awt.Rectangle
import kotlin.math.min
import kotlin.math.round

class ScrollState {
    var scale: Double = 0.0
        private set

    var documentWidth: Int = 0
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

    fun computeDimensions(editor: Editor, config: Config) {
        scale = config.pixelsPerLine.toDouble() / editor.lineHeight
        documentHeight = round(editor.contentComponent.height * scale).toInt()
        documentWidth = config.width
    }

    fun recomputeVisible(visibleArea: Rectangle) {
        visibleHeight = visibleArea.height
        drawHeight = min(visibleHeight, documentHeight)

        viewportStart = round(visibleArea.y * scale).toInt()
        viewportHeight = round(visibleArea.height * scale).toInt()

        visibleStart = round((viewportStart.toFloat() / (documentHeight - viewportHeight + 1)) * (documentHeight - visibleHeight + 1)).toInt().coerceAtLeast(0)
        visibleEnd = visibleStart + drawHeight
    }
}