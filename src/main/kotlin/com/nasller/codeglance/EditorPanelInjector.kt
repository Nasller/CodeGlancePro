package com.nasller.codeglance

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBPanel
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.config.SettingsChangeListener
import com.nasller.codeglance.config.SettingsChangePublisher
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.panel.vcs.MyVcsPanel
import java.awt.BorderLayout
import java.awt.Color

val CURRENT_EDITOR_DIFF_VIEW = Key<FrameDiffTool.DiffViewer>("CURRENT_EDITOR_DIFF_VIEW")

class EditorPanelInjector : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        if(event.editor.editorKind == EditorKind.DIFF) return
        val editorImpl = event.editor as? EditorImpl ?: return
        firstRunEditor(EditorInfo(editorImpl, if (CodeGlanceConfigService.getConfig().isRightAligned)
            BorderLayout.LINE_END else BorderLayout.LINE_START), null)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.putUserData(CURRENT_EDITOR_DIFF_VIEW, null)
    }
}

class DiffEditorPanelInjector : DiffExtension(){
    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        val userData = context.getUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE)
        if(userData == null || !userData.isCombinedDiffEnabled){
            viewer.diffEditorInjector()
        }
    }
}

class GlobalSettingsChangeListener : SettingsChangeListener{
    override fun onGlobalChanged() {
        processAllGlanceEditor { oldGlance, info ->
            oldGlance?.apply { Disposer.dispose(this) }
            if(info.editor.isDisableExtensionFile() || !CodeGlanceConfigService.getConfig().editorKinds.contains(info.editor.editorKind)) {
                oldGlance?.changeOriginScrollBarWidth(false)
            } else {
                if(info.editor.editorKind == EditorKind.DIFF) {
                    info.editor.getUserData(CURRENT_EDITOR_DIFF_VIEW)?.apply { diffEditorInjector() }
                } else {
                    setMyPanel(info).apply {
                        oldGlance?.let{ originalScrollbarWidth = it.originalScrollbarWidth }
                        changeOriginScrollBarWidth()
                    }
                }
            }
        }
    }
}

class GlobalLafManagerListener : LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) = SettingsChangePublisher.refreshDataAndImage()
}

private fun firstRunEditor(info: EditorInfo, diffView: FrameDiffTool.DiffViewer?) {
    if(diffView != null) info.editor.putUserData(CURRENT_EDITOR_DIFF_VIEW, diffView)
    if(info.editor.isDisableExtensionFile() || !CodeGlanceConfigService.getConfig().editorKinds.contains(info.editor.editorKind)) {
        return
    }
    val layout = info.editor.component.layout
    if (layout is BorderLayout && layout.getLayoutComponent(info.place) == null) {
        setMyPanel(info).apply { changeOriginScrollBarWidth() }
    }
}

private fun FrameDiffTool.DiffViewer.diffEditorInjector() {
    val config = CodeGlanceConfigService.getConfig()
    val where = if (config.isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
    when (this) {
        is UnifiedDiffViewer -> if(editor is EditorImpl) {
            firstRunEditor(EditorInfo(editor as EditorImpl, where),this)
        }
        is OnesideTextDiffViewer -> if(editor is EditorImpl) {
            firstRunEditor(EditorInfo(editor as EditorImpl, where),this)
        }
        is TwosideTextDiffViewer -> if(config.diffTwoSide) {
            editors.filterIsInstance<EditorImpl>().forEachIndexed { index, editor ->
                firstRunEditor(EditorInfo(editor, if (index == 0) BorderLayout.LINE_START else BorderLayout.LINE_END),this)
            }
        }
        is ThreesideTextDiffViewer -> if(config.diffThreeSide) {
            editors.filterIsInstance<EditorImpl>().forEachIndexed { index, editor ->
                if (index != 1 || config.diffThreeSideMiddle) {
                    firstRunEditor(EditorInfo(editor, if (index == 0) BorderLayout.LINE_START else BorderLayout.LINE_END),this)
                }
            }
        }
    }
}

private fun processAllGlanceEditor(action: (oldGlance:GlancePanel?, EditorInfo)->Unit){
    try {
        val where = if (CodeGlanceConfigService.getConfig().isRightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
        for (editor in EditorFactory.getInstance().allEditors.filterIsInstance<EditorImpl>()) {
            val info = EditorInfo(editor, where)
            val layout = info.editor.component.layout
            if (layout is BorderLayout) {
                action(((layout.getLayoutComponent(BorderLayout.LINE_END) ?:
                layout.getLayoutComponent(BorderLayout.LINE_START)) as? MyPanel)?.panel, info)
            }
        }
    }catch (e:Exception){
        e.printStackTrace()
    }
}

private fun setMyPanel(info: EditorInfo): GlancePanel {
    val glancePanel = GlancePanel(info)
    info.editor.component.add(MyPanel(glancePanel), info.place)
    glancePanel.hideScrollBarListener.addHideScrollBarListener()
    return glancePanel
}

private fun EditorImpl.isDisableExtensionFile(): Boolean{
    val extension = (virtualFile ?: FileDocumentManager.getInstance().getFile(document))?.run { fileType.defaultExtension } ?: ""
    return extension.isNotBlank() && CodeGlanceConfigService.getConfig().disableLanguageSuffix.split(",").toSet().contains(extension)
}

internal class MyPanel(val panel: GlancePanel?): JBPanel<MyPanel>(BorderLayout()){
    init{
        add(panel!!)
        if (CodeGlanceConfigService.getConfig().hideOriginalScrollBar){
            panel.myVcsPanel = MyVcsPanel(panel)
            add(panel.myVcsPanel!!, if (panel.getPlaceIndex() == GlancePanel.PlaceIndex.Left) BorderLayout.EAST else BorderLayout.WEST)
        }
    }

    override fun getBackground(): Color? = panel?.run { editor.contentComponent.background } ?: super.getBackground()
}

data class EditorInfo(val editor: EditorImpl, val place: String)