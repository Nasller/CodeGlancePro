package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.event.*

class MyVcsListener(private val myVcsPanel: MyVcsPanel,private val glancePanel: GlancePanel) : ComponentAdapter(),
    PrioritizedDocumentListener, VisibleAreaListener, HierarchyBoundsListener, HierarchyListener, Disposable {
    init {
        myVcsPanel.addHierarchyListener(this)
        myVcsPanel.addHierarchyBoundsListener(this)
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

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) = repaint()

    /** HierarchyListener */
    override fun hierarchyChanged(e: HierarchyEvent) = if(e.changeFlags == HierarchyEvent.PARENT_CHANGED.toLong()) repaint() else Unit

    private fun repaint() {
        if(myVcsPanel.isVisible && glancePanel.shouldUpdate()) myVcsPanel.repaint()
    }

    override fun dispose() {
        myVcsPanel.removeHierarchyListener(this)
        myVcsPanel.removeHierarchyBoundsListener(this)
        myVcsPanel.editor.contentComponent.removeComponentListener(this)
    }
}