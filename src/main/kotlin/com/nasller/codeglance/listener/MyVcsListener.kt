package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.event.*

class MyVcsListener(private val myVcsPanel: MyVcsPanel) : ComponentAdapter(), PrioritizedDocumentListener,
    VisibleAreaListener, HierarchyBoundsListener, HierarchyListener, Disposable {
    init {
        myVcsPanel.addHierarchyListener(this)
        myVcsPanel.addHierarchyBoundsListener(this)
        myVcsPanel.editor.contentComponent.addComponentListener(this)
        myVcsPanel.editor.document.addDocumentListener(this,myVcsPanel)
        myVcsPanel.editor.scrollingModel.addVisibleAreaListener(this,myVcsPanel)
    }
    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = myVcsPanel.repaint()

    /** PrioritizedDocumentListener */
    override fun documentChanged(event: DocumentEvent) = if(!event.document.isInBulkUpdate) myVcsPanel.repaint() else Unit

    override fun bulkUpdateFinished(document: Document) = myVcsPanel.repaint()

    override fun getPriority(): Int = 180 //EditorDocumentPriorities

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = myVcsPanel.repaint()

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) = myVcsPanel.repaint()

    /** HierarchyListener */
    override fun hierarchyChanged(e: HierarchyEvent) = if(e.changeFlags == HierarchyEvent.PARENT_CHANGED.toLong())
        myVcsPanel.repaint() else Unit

    override fun dispose() {
        myVcsPanel.removeHierarchyListener(this)
        myVcsPanel.removeHierarchyBoundsListener(this)
        myVcsPanel.editor.contentComponent.removeComponentListener(this)
    }
}