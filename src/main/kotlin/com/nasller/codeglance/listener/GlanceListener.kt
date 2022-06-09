package com.nasller.codeglance.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.util.Alarm
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.*

class GlanceListener(private val glancePanel: GlancePanel) : ComponentAdapter(), FoldingListener, MarkupModelListener,
    SettingsChangeListener, CaretListener, PrioritizedDocumentListener, VisibleAreaListener, SelectionListener,
    HierarchyBoundsListener, HierarchyListener, SoftWrapChangeListener {
    private val alarm = Alarm(glancePanel)
    init {
        ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
    }
    /** FoldingListener */
    override fun onFoldRegionStateChange(region: FoldRegion) = glancePanel.updateImage()

    /** SoftWrapChangeListener */
    override fun softWrapsChanged() = glancePanel.updateImage()

    override fun recalculationEnds() = Unit

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

    private fun updateRangeHighlight(highlighter: RangeHighlighterEx) {
        if (highlighter.editorFilter.avaliableIn(glancePanel.editor)) {
            if(alarm.activeRequestCount == 0){
                alarm.addRequest({ glancePanel.updateImage() }, 150)
            }
        } else Unit
    }

    /** CaretListener */
    override fun caretPositionChanged(event: CaretEvent) = glancePanel.repaint()

    override fun caretAdded(event: CaretEvent) = glancePanel.repaint()

    override fun caretRemoved(event: CaretEvent) = glancePanel.repaint()

    /** SelectionListener */
    override fun selectionChanged(e: SelectionEvent) = glancePanel.repaint()

    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent) {
        glancePanel.updateScrollState()
        glancePanel.repaint()
    }

    /** DocumentListener */
    override fun documentChanged(event: DocumentEvent) = if(!event.document.isInBulkUpdate) glancePanel.updateImage() else Unit

    override fun bulkUpdateFinished(document: Document) = glancePanel.updateImage()

    override fun getPriority(): Int = 170 //EditorDocumentPriorities

    /** SettingsChangeListener */
    override fun onRefreshChanged() {
        glancePanel.refresh()
        glancePanel.changeOriginScrollBarWidth()
    }

    override fun onHoveringOriginalScrollBarChanged(value:Boolean) {
        if(value){
            glancePanel.addHideScrollBarListener()
        }else{
            glancePanel.removeHideScrollBarListener()
        }
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

class GlanceOtherListener(private val glancePanel: GlancePanel) : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) = if(isAvailable(highlighter)) glancePanel.repaint() else Unit

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = if(isAvailable(highlighter)) glancePanel.repaint() else Unit

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean,
        fontStyleChanged: Boolean, foregroundColorChanged: Boolean) = if(isAvailable(highlighter)) glancePanel.repaint() else Unit

    private fun isAvailable(highlighter: RangeHighlighterEx):Boolean = highlighter.getErrorStripeMarkColor(glancePanel.editor.colorsScheme) != null
}