package com.nasller.codeglance.listener

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.ui.scale.UserScaleContext
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.config.enums.EditorSizeEnum
import com.nasller.codeglance.panel.GlancePanel
import java.awt.event.*

class GlanceListener(private val glancePanel: GlancePanel) : ComponentAdapter(), SettingsChangeListener, CaretListener,
	VisibleAreaListener, SelectionListener, HierarchyBoundsListener, HierarchyListener, UISettingsListener, UserScaleContext.UpdateListener, Disposable {
	init {
		glancePanel.addHierarchyListener(this)
		glancePanel.addHierarchyBoundsListener(this)
		glancePanel.scaleContext.addUpdateListener(this)
		glancePanel.editor.let {
			it.contentComponent.addComponentListener(this)
			it.selectionModel.addSelectionListener(this, glancePanel)
			it.scrollingModel.addVisibleAreaListener(this, glancePanel)
			it.caretModel.addCaretListener(this, glancePanel)
			it.markupModel.addMarkupModelListener(glancePanel, GlanceOtherListener(glancePanel))
		}
		ApplicationManager.getApplication().messageBus.connect(glancePanel).apply {
			subscribe(SettingsChangeListener.TOPIC, this@GlanceListener)
			subscribe(UISettingsListener.TOPIC, this@GlanceListener)
		}
	}

	/** CaretListener */
	override fun caretPositionChanged(event: CaretEvent) {
		if(event.oldPosition.line != event.newPosition.line) {
			repaint()
		}
	}

	override fun caretAdded(event: CaretEvent) = repaint()

	override fun caretRemoved(event: CaretEvent) = repaint()

	/** SelectionListener */
	override fun selectionChanged(e: SelectionEvent) = repaint()

	/** ComponentAdapter */
	override fun componentResized(componentEvent: ComponentEvent) = glancePanel.run {
		if(glancePanel.updateScrollState()){
			repaint()
		}
	}

	/** SettingsChangeListener */
	override fun onHoveringOriginalScrollBarChanged(value: Boolean) = if (value) glancePanel.hideScrollBarListener.addHideScrollBarListener()
	else glancePanel.hideScrollBarListener.removeHideScrollBarListener()

	override fun refreshDataAndImage() = glancePanel.refreshDataAndImage()

	/** VisibleAreaListener */
	override fun visibleAreaChanged(e: VisibleAreaEvent) {
		if(glancePanel.config.editorSize == EditorSizeEnum.Fit){
			if(glancePanel.updateScrollState(e.newRectangle)){
				repaint()
			}
		}else {
			glancePanel.scrollState.recomputeVisible(e.newRectangle)
			repaint()
		}
	}

	/** HierarchyBoundsListener */
	override fun ancestorMoved(e: HierarchyEvent) {}

	override fun ancestorResized(e: HierarchyEvent) {
		if (checkWithGlance {config.autoCalWidthInSplitterMode && !config.hoveringToShowScrollBar}){
			glancePanel.refresh()
		}
	}

	/** HierarchyListener */
	override fun hierarchyChanged(e: HierarchyEvent) {
		if (checkWithGlance {config.autoCalWidthInSplitterMode && !config.hoveringToShowScrollBar} &&
			e.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) glancePanel.refresh()
	}

	/** UISettingsListener */
	override fun uiSettingsChanged(uiSettings: UISettings) {
		glancePanel.scaleContext.update()
	}

	/** UserScaleContext.UpdateListener */
	override fun contextUpdated() {
		glancePanel.updateScrollState()
		glancePanel.refreshDataAndImage()
	}

	private fun repaint() = if (checkWithGlance()) glancePanel.repaint() else Unit

	private fun checkWithGlance(predicate:(GlancePanel.()->Boolean)? = null) = glancePanel.checkVisible() &&
			(predicate == null || predicate.invoke(glancePanel))

	override fun dispose() {
		glancePanel.removeHierarchyListener(this)
		glancePanel.removeHierarchyBoundsListener(this)
		glancePanel.editor.contentComponent.removeComponentListener(this)
	}
}

class GlanceOtherListener(private val glancePanel: GlancePanel) : MarkupModelListener {
	override fun afterAdded(highlighter: RangeHighlighterEx) = repaint(highlighter)

	override fun beforeRemoved(highlighter: RangeHighlighterEx) = repaint(highlighter)

	private fun repaint(highlighter: RangeHighlighterEx) {
		if (glancePanel.run { highlighter.getMarkupColor() } != null && glancePanel.checkVisible()) {
			glancePanel.repaint()
		}
	}
}