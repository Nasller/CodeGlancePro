package com.nasller.codeglance.render

import com.intellij.openapi.editor.Editor
import com.nasller.codeglance.panel.AbstractGlancePanel
import java.awt.Rectangle
import kotlin.math.min

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

    fun computeDimensions(editor: Editor, glancePanel: AbstractGlancePanel) {
        scale = glancePanel.config.pixelsPerLine.toDouble() / editor.lineHeight
        documentHeight = (editor.contentComponent.height * scale).toInt()
        documentWidth = glancePanel.preferredSize.width
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