// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.nasller.codeglance.extensions.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import com.nasller.codeglance.util.message
import org.jetbrains.annotations.Nls

class GlanceVisibleWidgetFactory : StatusBarEditorBasedWidgetFactory() {
	override fun getId(): String = GlanceToggleVisibleWidgetPanel.ID

	@Nls
	override fun getDisplayName(): String = message("glance.widget")

	override fun createWidget(project: Project): StatusBarWidget = GlanceToggleVisibleWidgetPanel(project)

	override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}