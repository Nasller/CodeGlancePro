package com.nasller.codeglance.listener

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.nasller.codeglance.panel.MyVcsPanel
import java.awt.event.*

class MyVcsListener(private val myVcsPanel: MyVcsPanel) : ComponentAdapter(), FoldingListener, DocumentListener, VisibleAreaListener,
    HierarchyBoundsListener, HierarchyListener {
    /** FoldingListener */
    override fun onFoldRegionStateChange(region: FoldRegion) = myVcsPanel.repaint()

    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = myVcsPanel.repaint()

    /** DocumentListener */
    override fun documentChanged(event: DocumentEvent) = myVcsPanel.repaint()

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) = myVcsPanel.repaint()

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) = myVcsPanel.repaint()

    /** HierarchyListener */
    override fun hierarchyChanged(e: HierarchyEvent) = if(e.changeFlags == HierarchyEvent.PARENT_CHANGED.toLong())
        myVcsPanel.repaint() else Unit

}