package com.nasller.codeglance.listener

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.nasller.codeglance.panel.AbstractGlancePanel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent

class GlanceListener(private val glancePanel: AbstractGlancePanel) : ComponentAdapter(), FoldingListener, MarkupModelListener,
    CaretListener, DocumentListener, VisibleAreaListener,SelectionListener, HierarchyBoundsListener {
    /** FoldingListener */
    override fun onFoldRegionStateChange(region: FoldRegion) = glancePanel.updateImage()

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) = glancePanel.repaint()

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = glancePanel.repaint()

    override fun attributesChanged(highlighter: RangeHighlighterEx,
                                   renderersChanged: Boolean, fontStyleChanged: Boolean, foregroundColorChanged: Boolean
    ) = if(renderersChanged || foregroundColorChanged)glancePanel.repaint() else Unit

    /** CaretListener */
    override fun caretPositionChanged(event: CaretEvent) = glancePanel.repaint()

    override fun caretAdded(event: CaretEvent) = glancePanel.repaint()

    override fun caretRemoved(event: CaretEvent) = glancePanel.repaint()

    /** SelectionListener */
    override fun selectionChanged(e: SelectionEvent) = glancePanel.repaint()

    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent?) = glancePanel.updateImage()

    /** DocumentListener */
    override fun documentChanged(event: DocumentEvent) = glancePanel.updateImage()

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
        glancePanel.scrollState.recomputeVisible(e.newRectangle)
        glancePanel.repaint()
    }

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) = glancePanel.refresh()
}