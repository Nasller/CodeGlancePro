package com.nasller.codeglance.render

import com.nasller.codeglance.config.enums.EditorSizeEnum
import com.nasller.codeglance.panel.GlancePanel
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

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
        val curDocumentHeight = (contentHeight * newScale).toInt()
        if(config.editorSize == EditorSizeEnum.Fit && curDocumentHeight > visibleArea.height) {
            if(visibleArea.height < 1 && initialized) {
                return true
            }
            val oldDocumentHeight = documentHeight.apply { documentHeight = visibleArea.height }
            scale = documentHeight.toDouble() / contentHeight
            pixelsPerLine = scale * lineHeight
            if((oldDocumentHeight > 0 || !initialized) && oldDocumentHeight != documentHeight) {
                initialized = true
                if(visibleChange && documentHeight > 0 && pixelsPerLine > 0) {
                    refreshDataAndImage()
                    return false
                }
            }
        }else {
            pixelsPerLine = config.pixelsPerLine.toDouble()
            documentHeight = curDocumentHeight
            val oldScale = scale.apply { scale = newScale }
            if(visibleChange && !oldScale.isNaN() && oldScale != scale) {
                initialized = true
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

    fun getRenderHeight() = max(1.0, pixelsPerLine).toInt()

    public override fun clone(): ScrollState {
        return super.clone() as ScrollState
    }
}