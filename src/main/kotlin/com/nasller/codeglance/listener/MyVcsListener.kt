package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.util.Disposer
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class MyVcsListener(private val myVcsPanel: MyVcsPanel) : ComponentAdapter(), PrioritizedDocumentListener, VisibleAreaListener,
    MarkupModelListener, Disposable {
    init {
        val editor = myVcsPanel.glancePanel.editor
        val disposable = editor.disposable
        Disposer.register(disposable, this)
        editor.contentComponent.addComponentListener(this)
        editor.document.addDocumentListener(this, disposable)
        editor.scrollingModel.addVisibleAreaListener(this, disposable)
        editor.filteredDocumentMarkupModel.addMarkupModelListener(disposable, this)
    }
    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = repaint()

    /** PrioritizedDocumentListener */
    override fun documentChanged(event: DocumentEvent) = if(!event.document.isInBulkUpdate) repaint() else Unit

    override fun bulkUpdateFinished(document: Document) = repaint()

    override fun getPriority(): Int = 180 //EditorDocumentPriorities

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = repaint()

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) = repaint(highlighter)

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = repaint(highlighter)

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleChanged: Boolean) = repaint(highlighter)

    private fun repaint(highlighter: RangeHighlighterEx? = null) {
        val editor = myVcsPanel.glancePanel.editor
        if(myVcsPanel.isVisible && (highlighter == null ||
                    (highlighter.isThinErrorStripeMark && highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null))) myVcsPanel.repaint()
    }

    override fun dispose() {
        myVcsPanel.glancePanel.editor.contentComponent.removeComponentListener(this)
    }
}