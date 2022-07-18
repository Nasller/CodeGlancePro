package com.nasller.codeglance.extensions.widget

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.util.Consumer
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.panel.AbstractGlancePanel
import com.nasller.codeglance.util.CodeGlanceIcons
import com.nasller.codeglance.util.message
import java.awt.event.MouseEvent
import javax.swing.Icon

class GlanceToggleVisibleWidgetPanel : StatusBarWidget, StatusBarWidget.IconPresentation {
	private val config = ConfigInstance.state
	private var myStatusBar: StatusBar? = null

	override fun getIcon(): Icon? {
		if(config.hoveringToShowScrollBar) return null
		val editor = getEditor()?: return null
		return if (isVisible(editor)) CodeGlanceIcons.GlanceShow else CodeGlanceIcons.GlanceHide
	}

	override fun ID(): String = ID

	override fun getPresentation(): WidgetPresentation = this

	override fun dispose() {
		myStatusBar = null
	}

	override fun install(statusBar: StatusBar) {
		myStatusBar = statusBar
		updateStatusBar()
		val project = statusBar.project ?: return
		val connect = project.messageBus.connect(this)
		connect.subscribe(SettingsChangeListener.TOPIC, object : SettingsChangeListener {
			override fun onRefreshChanged() = updateStatusBar()

			override fun onHoveringOriginalScrollBarChanged(value: Boolean) = updateStatusBar()

			override fun onGlobalChanged() = updateStatusBar()
		})
		connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
			override fun selectionChanged(event: FileEditorManagerEvent) = updateStatusBar()
		})
	}

	override fun getTooltipText(): String? {
		val editor = getEditor()?: return null
		return if (isVisible(editor)) message("glance.visible.show") else message("glance.visible.hide")
	}

	override fun getClickConsumer(): Consumer<MouseEvent> = Consumer{
		getEditor()?.getUserData(AbstractGlancePanel.CURRENT_GLANCE)?.apply{
			isVisible = !isVisible
			if(isVisible) refresh(true, directUpdate = true)
			changeOriginScrollBarWidth(isVisible)
		}
		updateStatusBar()
	}

	private fun isVisible(editor: Editor) = editor.getUserData(AbstractGlancePanel.CURRENT_GLANCE)?.isVisible == false

	private fun getEditor(): Editor? {
		val project = myStatusBar?.project ?: return null
		val fileEditor = StatusBarUtil.getCurrentFileEditor(myStatusBar) ?: FileEditorManagerEx.getInstanceEx(project).selectedEditor
		return if (fileEditor is TextEditor) fileEditor.editor else null
	}

	private fun updateStatusBar() {
		myStatusBar?.updateWidget(ID())
	}

	companion object {
		const val ID = "CodeGlanceWidget"
	}
}