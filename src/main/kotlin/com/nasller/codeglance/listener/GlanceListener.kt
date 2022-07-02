package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.util.SingleAlarm
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.*

class GlanceListener(private val glancePanel: GlancePanel) : ComponentAdapter(), FoldingListener, MarkupModelListener,
    SettingsChangeListener, CaretListener, PrioritizedDocumentListener, VisibleAreaListener, SelectionListener,
    HierarchyBoundsListener, HierarchyListener, SoftWrapChangeListener,Disposable {
    private var softWrapEnabled = false
    private val alarm = SingleAlarm({ glancePanel.updateImage(true) }, 500, glancePanel)
    init {
        glancePanel.addHierarchyListener(this)
        glancePanel.addHierarchyBoundsListener(this)
        val editor = glancePanel.editor
        editor.contentComponent.addComponentListener(this)
        editor.document.addDocumentListener(this,glancePanel)
        editor.selectionModel.addSelectionListener(this,glancePanel)
        editor.scrollingModel.addVisibleAreaListener(this,glancePanel)
        editor.foldingModel.addListener(this,glancePanel)
        editor.softWrapModel.addSoftWrapChangeListener(this)
        editor.caretModel.addCaretListener(this,glancePanel)
        editor.markupModel.addMarkupModelListener(glancePanel, GlanceOtherListener(glancePanel))
        editor.filteredDocumentMarkupModel.addMarkupModelListener(glancePanel, this)
        ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
    }
    /** FoldingListener */
    override fun onFoldProcessingEnd() = glancePanel.updateImage()

    /** SoftWrapChangeListener */
    override fun softWrapsChanged() {
        val enabled = glancePanel.editor.softWrapModel.isSoftWrappingEnabled
        if(enabled && !softWrapEnabled){
            softWrapEnabled = true
            glancePanel.updateImage()
        }else if(!enabled && softWrapEnabled){
            softWrapEnabled = false
            glancePanel.updateImage()
        }
    }

    override fun recalculationEnds() = Unit

    /** MarkupModelListener */
    override fun afterAdded(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter)

    private fun updateRangeHighlight(highlighter: RangeHighlighterEx) {
        if (EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(glancePanel.editor.colorsScheme))
            && glancePanel.shouldUpdate()) alarm.cancelAndRequest()
    }

    /** CaretListener */
    override fun caretPositionChanged(event: CaretEvent) = repaint()

    override fun caretAdded(event: CaretEvent) = repaint()

    override fun caretRemoved(event: CaretEvent) = repaint()

    /** SelectionListener */
    override fun selectionChanged(e: SelectionEvent) = repaint()

    /** ComponentAdapter */
    override fun componentResized(componentEvent: ComponentEvent) {
        if (glancePanel.shouldUpdate()) {
            glancePanel.updateScrollState()
            glancePanel.repaint()
        }
    }

    /** PrioritizedDocumentListener */
    override fun documentChanged(event: DocumentEvent) {
        if(!event.document.isInBulkUpdate) {
            if(event.document.lineCount > glancePanel.config.moreThanLineDelay) {
                if(glancePanel.shouldUpdate()) alarm.cancelAndRequest()
            } else glancePanel.updateImage()
        }
    }

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
        if(glancePanel.shouldUpdate()){
            glancePanel.scrollState.recomputeVisible(e.newRectangle)
            glancePanel. repaint()
        }
    }

    /** HierarchyBoundsListener */
    override fun ancestorMoved(e: HierarchyEvent) {}

    override fun ancestorResized(e: HierarchyEvent) {
        if(glancePanel.shouldUpdate()) glancePanel.refresh(false)
    }

    /** HierarchyListener */
    override fun hierarchyChanged(e: HierarchyEvent) {
        if(e.changeFlags == HierarchyEvent.PARENT_CHANGED.toLong() && glancePanel.shouldUpdate()) glancePanel.refresh(false)
    }

    private fun repaint() {
        if(glancePanel.shouldUpdate()) glancePanel.repaint()
    }

    override fun dispose() {
        glancePanel.removeHierarchyListener(this)
        glancePanel.removeHierarchyBoundsListener(this)
        glancePanel.editor.contentComponent.removeComponentListener(this)
    }
}

class GlanceOtherListener(private val glancePanel: GlancePanel) : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) = repaint(highlighter)

    override fun beforeRemoved(highlighter: RangeHighlighterEx) = repaint(highlighter)

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean,
        fontStyleChanged: Boolean, foregroundColorChanged: Boolean) = repaint(highlighter)

    private fun repaint(highlighter: RangeHighlighterEx) {
        if(highlighter.getErrorStripeMarkColor(glancePanel.editor.colorsScheme) != null && glancePanel.shouldUpdate()){
            glancePanel.repaint()
        }
    }
}