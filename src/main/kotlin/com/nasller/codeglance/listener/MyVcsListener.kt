package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class MyVcsListener(private val myVcsPanel: MyVcsPanel,private val glancePanel: GlancePanel) : ComponentAdapter(),
    PrioritizedDocumentListener, VisibleAreaListener, Disposable {
    init {
        myVcsPanel.editor.contentComponent.addComponentListener(this)
        myVcsPanel.editor.document.addDocumentListener(this,myVcsPanel)
        myVcsPanel.editor.scrollingModel.addVisibleAreaListener(this,myVcsPanel)
    }
    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = repaint()

    /** PrioritizedDocumentListener */
    override fun documentChanged(event: DocumentEvent) = if(!event.document.isInBulkUpdate) repaint() else Unit

    override fun bulkUpdateFinished(document: Document) = repaint()

    override fun getPriority(): Int = 180 //EditorDocumentPriorities

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = repaint()

    private fun repaint() {
        if(myVcsPanel.isVisible && glancePanel.shouldUpdate()) glancePanel.vcsRenderService?.trackerManager?.invokeAfterUpdate {
            myVcsPanel.repaint()
        }
    }

    override fun dispose() {
        myVcsPanel.editor.contentComponent.removeComponentListener(this)
    }
}