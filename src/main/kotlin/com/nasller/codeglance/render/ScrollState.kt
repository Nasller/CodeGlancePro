package com.nasller.codeglance.render

import com.intellij.openapi.editor.Editor
import com.nasller.codeglance.config.Config
import java.awt.Rectangle
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollState {
    var scale: Float = 0f
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
    var drawHeight: Int = 0
        private set

    var viewportStart: Int = 0
        private set
    var viewportHeight: Int = 0
        private set

    fun computeDimensions(editor: Editor, config: Config) {
        scale = config.pixelsPerLine.toFloat() / editor.lineHeight
        documentHeight = (editor.contentComponent.height * scale).roundToInt()
        documentWidth = config.width
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