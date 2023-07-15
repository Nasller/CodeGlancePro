package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class MyVcsListener(private val myVcsPanel: MyVcsPanel) : ComponentAdapter(), VisibleAreaListener, MarkupModelListener, Disposable {
    init {
        myVcsPanel.glancePanel.editor.let {
            it.contentComponent.addComponentListener(this)
            it.scrollingModel.addVisibleAreaListener(this, this)
            it.filteredDocumentMarkupModel.addMarkupModelListener(this, this)
        }
    }
    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = repaint()

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = repaint()

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) = repaint(highlighter)

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = repaint(highlighter)

    private fun repaint(highlighter: RangeHighlighterEx? = null) {
        val editor = myVcsPanel.glancePanel.editor
        if(myVcsPanel.isVisible && (highlighter == null || (highlighter.isThinErrorStripeMark &&
                    highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null))) myVcsPanel.repaint()
    }

    override fun dispose() {
        myVcsPanel.glancePanel.editor.contentComponent.removeComponentListener(this)
    }
}