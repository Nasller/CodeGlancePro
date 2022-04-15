package com.nasller.codeglance.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.AbstractGlancePanel
import com.nasller.codeglance.util.attributesImpactForegroundColor
import java.awt.event.*

class GlanceListener(private val glancePanel: AbstractGlancePanel) : ComponentAdapter(), FoldingListener, MarkupModelListener,
    SettingsChangeListener, CaretListener, DocumentListener, VisibleAreaListener,SelectionListener,
    HierarchyBoundsListener, HierarchyListener {
    init {
        ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
    }
    /** FoldingListener */
    override fun onFoldRegionStateChange(region: FoldRegion) = glancePanel.updateImage()

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) =
        if (attributesImpactForegroundColor(highlighter.getTextAttributes(glancePanel.editor.colorsScheme)))glancePanel.updateImageSoon()
        else glancePanel.repaint()

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = glancePanel.repaint()

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean,
                                   fontStyleChanged: Boolean, foregroundColorChanged: Boolean
    ) = if(renderersChanged || foregroundColorChanged)glancePanel.updateImageSoon() else glancePanel.repaint()

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

    /** SettingsChangeListener */
    override fun onRefreshChanged() {
        glancePanel.refresh()
        glancePanel.changeOriginScrollBarWidth()
    }

    /** VisibleAreaListener */
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
        glancePanel.scrollState.recomputeVisible(e.newRectangle)
        glancePanel.repaint()
    }

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) = glancePanel.refresh(false)

    /** HierarchyListener */
    override fun hierarchyChanged(e: HierarchyEvent) = if(e.changeFlags == HierarchyEvent.PARENT_CHANGED.toLong())
        glancePanel.refresh(false) else Unit

}