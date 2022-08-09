package com.nasller.codeglance.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.Inlay.Placement
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
	HierarchyBoundsListener, HierarchyListener, SoftWrapChangeListener, InlayModel.Listener, Disposable {
	private val editor = glancePanel.editor
	private var softWrapEnabled = false
	private val alarm = SingleAlarm({ glancePanel.updateImage(true) }, 500, glancePanel)
	init {
		glancePanel.addHierarchyListener(this)
		glancePanel.addHierarchyBoundsListener(this)
		val editor = editor
		editor.contentComponent.addComponentListener(this)
		editor.document.addDocumentListener(this, glancePanel)
		editor.selectionModel.addSelectionListener(this, glancePanel)
		editor.scrollingModel.addVisibleAreaListener(this, glancePanel)
		editor.foldingModel.addListener(this, glancePanel)
		editor.inlayModel.addListener(this, glancePanel)
		editor.softWrapModel.addSoftWrapChangeListener(this)
		editor.caretModel.addCaretListener(this, glancePanel)
		editor.markupModel.addMarkupModelListener(glancePanel, GlanceOtherListener(glancePanel))
		editor.filteredDocumentMarkupModel.addMarkupModelListener(glancePanel, this)
		ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
	}

	/** FoldingListener */
	override fun onFoldProcessingEnd() {
		if (editor.document.isInBulkUpdate) return
		glancePanel.updateImage()
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED != 0 &&
			!editor.foldingModel.isInBatchFoldingOperation
		) repaintOrRequest(true)
	}

	/** InlayModel.Listener */
	override fun onAdded(inlay: Inlay<*>) {
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode
			|| inlay.placement != Placement.ABOVE_LINE
		) return
		repaintOrRequest(true)
	}

	override fun onRemoved(inlay: Inlay<*>) {
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode
			|| inlay.placement != Placement.ABOVE_LINE
		) return
		repaintOrRequest(true)
	}

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode ||
			inlay.placement != Placement.ABOVE_LINE || changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0
		) return
		repaintOrRequest(true)
	}

	override fun onBatchModeFinish(editor: Editor) {
		if (editor.document.isInBulkUpdate) return
		glancePanel.updateImage()
	}

	/** SoftWrapChangeListener */
	override fun softWrapsChanged() {
		val enabled = editor.softWrapModel.isSoftWrappingEnabled
		if (enabled && !softWrapEnabled) {
			softWrapEnabled = true
			glancePanel.updateImage()
		} else if (!enabled && softWrapEnabled) {
			softWrapEnabled = false
			glancePanel.updateImage()
		}
	}

	override fun recalculationEnds() = Unit

	/** MarkupModelListener */
	override fun afterAdded(highlighter: RangeHighlighterEx) =
		updateRangeHighlight(highlighter)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) =
		updateRangeHighlight(highlighter)

	override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleChanged: Boolean) =
		updateRangeHighlight(highlighter)

	private fun updateRangeHighlight(highlighter: RangeHighlighterEx) {
		//如果开启隐藏滚动条则忽略Vcs高亮
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode
			|| (glancePanel.config.hideOriginalScrollBar && highlighter.isThinErrorStripeMark)) return
		repaintOrRequest(EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme)))
	}

	/** CaretListener */
	override fun caretPositionChanged(event: CaretEvent) = repaintOrRequest()

	override fun caretAdded(event: CaretEvent) = repaintOrRequest()

	override fun caretRemoved(event: CaretEvent) = repaintOrRequest()

	/** SelectionListener */
	override fun selectionChanged(e: SelectionEvent) = repaintOrRequest()

	/** ComponentAdapter */
	override fun componentResized(componentEvent: ComponentEvent) {
		glancePanel.updateScrollState()
		repaintOrRequest()
	}

	/** PrioritizedDocumentListener */
	override fun documentChanged(event: DocumentEvent) {
		if (event.document.isInBulkUpdate) return
		if (event.document.lineCount > glancePanel.config.moreThanLineDelay) {
			repaintOrRequest(true)
		} else glancePanel.updateImage()
	}

	override fun bulkUpdateFinished(document: Document) = glancePanel.updateImage()

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	/** SettingsChangeListener */
	override fun onHoveringOriginalScrollBarChanged(value: Boolean) = if (value) glancePanel.addHideScrollBarListener()
	else glancePanel.removeHideScrollBarListener()

	/** VisibleAreaListener */
	override fun visibleAreaChanged(e: VisibleAreaEvent) {
		glancePanel.scrollState.recomputeVisible(e.newRectangle)
		repaintOrRequest()
	}

	/** HierarchyBoundsListener */
	override fun ancestorMoved(e: HierarchyEvent) {}

	override fun ancestorResized(e: HierarchyEvent) {
		if (glancePanel.checkVisible()) glancePanel.refresh(false)
	}

	/** HierarchyListener */
	override fun hierarchyChanged(e: HierarchyEvent) {
		if (e.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L && glancePanel.checkVisible()) glancePanel.refresh(false)
	}

	private fun repaintOrRequest(request: Boolean = false) {
		if (glancePanel.checkVisible()) {
			if (request) alarm.cancelAndRequest()
			else glancePanel.repaint()
		}
	}

	override fun dispose() {
		glancePanel.removeHierarchyListener(this)
		glancePanel.removeHierarchyBoundsListener(this)
		editor.contentComponent.removeComponentListener(this)
	}
}

class GlanceOtherListener(private val glancePanel: GlancePanel) : MarkupModelListener {
	override fun afterAdded(highlighter: RangeHighlighterEx) = repaint(highlighter)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) = repaint(highlighter)

	private fun repaint(highlighter: RangeHighlighterEx) {
		if (highlighter.getErrorStripeMarkColor(glancePanel.editor.colorsScheme) != null && glancePanel.checkVisible()) {
			glancePanel.repaint()
		}
	}
}