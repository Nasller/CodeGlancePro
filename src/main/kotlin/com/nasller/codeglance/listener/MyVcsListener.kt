package com.nasller.codeglance.listener

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.nasller.codeglance.panel.vcs.MyVcsPanel

class MyVcsListener(private val myVcsPanel: MyVcsPanel) : PrioritizedDocumentListener, VisibleAreaListener {
    init {
        val editor = myVcsPanel.glancePanel.editor
        editor.document.addDocumentListener(this,editor.disposable)
        editor.scrollingModel.addVisibleAreaListener(this,editor.disposable)
    }
    /** PrioritizedDocumentListener */
    override fun documentChanged(event: DocumentEvent) = if(!event.document.isInBulkUpdate) myVcsPanel.repaint() else Unit

    override fun bulkUpdateFinished(document: Document) = myVcsPanel.repaint()

    override fun getPriority(): Int = 180 //EditorDocumentPriorities

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = myVcsPanel.repaint()
}