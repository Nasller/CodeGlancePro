package com.nasller.codeglance.listener

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.Inlay.Placement
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

@Suppress("UnstableApiUsage")
class GlanceListener(private val glancePanel: GlancePanel) : ComponentAdapter(), FoldingListener, MarkupModelListener,
	SettingsChangeListener, CaretListener, PrioritizedDocumentListener, VisibleAreaListener, SelectionListener,
	HierarchyBoundsListener, HierarchyListener, SoftWrapChangeListener, InlayModel.Listener, PropertyChangeListener, Disposable {
	private val editor
		get() = glancePanel.editor
	private var softWrapEnabled = false
	init {
		glancePanel.addHierarchyListener(this)
		glancePanel.addHierarchyBoundsListener(this)
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
		editor.addPropertyChangeListener(this,this)
		ApplicationManager.getApplication().messageBus.connect(glancePanel).subscribe(SettingsChangeListener.TOPIC, this)
	}

	/** FoldingListener */
	override fun onFoldProcessingEnd() {
		if (editor.document.isInBulkUpdate) return
		glancePanel.updateImage()
	}

	override fun onCustomFoldRegionPropertiesChange(region: CustomFoldRegion, flags: Int) {
		if (flags and FoldingListener.ChangeFlags.HEIGHT_CHANGED != 0 && !editor.document.isInBulkUpdate) repaintOrRequest(true)
	}

	/** InlayModel.Listener */
	override fun onAdded(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onRemoved(inlay: Inlay<*>) = checkinInlayAndUpdate(inlay)

	override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) = checkinInlayAndUpdate(inlay, changeFlags)

	private fun checkinInlayAndUpdate(inlay: Inlay<*>, changeFlags: Int? = null) {
		if(editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || inlay.placement != Placement.ABOVE_LINE
			|| !inlay.isValid || (changeFlags != null && changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED == 0)) return
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
	override fun afterAdded(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,false)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) = updateRangeHighlight(highlighter,true)

	private fun updateRangeHighlight(highlighter: RangeHighlighterEx,remove: Boolean) {
		//如果开启隐藏滚动条则忽略Vcs高亮
		val highlightChange = glancePanel.markCommentState.markCommentHighlightChange(highlighter, remove)
		if (editor.document.isInBulkUpdate || editor.inlayModel.isInBatchMode || editor.foldingModel.isInBatchFoldingOperation
			|| (glancePanel.config.hideOriginalScrollBar && highlighter.isThinErrorStripeMark)) return
		if(highlightChange || EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme))) {
			repaintOrRequest(true)
		} else if(highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null){
			repaintOrRequest()
		}
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
		//console delay update
		if (ConsoleViewUtil.isConsoleViewEditor(editor) || event.document.lineCount > glancePanel.config.moreThanLineDelay) {
			repaintOrRequest(true)
		} else glancePanel.updateImage()
	}

	override fun bulkUpdateFinished(document: Document) = glancePanel.updateImage()

	override fun getPriority(): Int = 170 //EditorDocumentPriorities

	/** SettingsChangeListener */
	override fun onHoveringOriginalScrollBarChanged(value: Boolean) = if (value) glancePanel.hideScrollBarListener.addHideScrollBarListener()
	else glancePanel.hideScrollBarListener.removeHideScrollBarListener()

	override fun refresh(refreshImage: Boolean) = glancePanel.refreshWithWidth(refreshImage)

	/** VisibleAreaListener */
	override fun visibleAreaChanged(e: VisibleAreaEvent) {
		glancePanel.scrollState.recomputeVisible(e.newRectangle)
		repaintOrRequest()
	}

	/** HierarchyBoundsListener */
	override fun ancestorMoved(e: HierarchyEvent) {}

	override fun ancestorResized(e: HierarchyEvent) {
		if (checkWithGlance {config.autoCalWidthInSplitterMode && !config.hoveringToShowScrollBar}) glancePanel.refreshWithWidth(refreshImage = false)
	}

	/** HierarchyListener */
	override fun hierarchyChanged(e: HierarchyEvent) {
		if (checkWithGlance {config.autoCalWidthInSplitterMode && !config.hoveringToShowScrollBar} &&
			e.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) glancePanel.refreshWithWidth(refreshImage = false)
	}

	/** PropertyChangeListener */
	override fun propertyChange(evt: PropertyChangeEvent) {
		if (EditorEx.PROP_HIGHLIGHTER != evt.propertyName || evt.newValue is EmptyEditorHighlighter) return
		glancePanel.updateImage()
	}

	private fun repaintOrRequest(request: Boolean = false) {
		if (checkWithGlance()) {
			if (request) glancePanel.delayUpdateImage()
			else glancePanel.repaint()
		}
	}

	private fun checkWithGlance(predicate:(GlancePanel.()->Boolean)? = null) = glancePanel.checkVisible() && (predicate == null || predicate.invoke(glancePanel))

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