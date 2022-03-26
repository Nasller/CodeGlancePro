package com.nasller.codeglance.panel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ReadTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.psi.PsiDocumentManager
import com.nasller.codeglance.render.Folds
import com.nasller.codeglance.render.OldMinimap
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.lang.ref.SoftReference

class OldGlancePanel(private val project: Project, textEditor: TextEditor) : AbstractGlancePanel<OldMinimap>(project,textEditor) {
    init {
        Disposer.register(textEditor, this)
        scrollbar = ScrollBar(textEditor, scrollState,this)
        add(scrollbar)
        val foldListener = object : FoldingListener {
            override fun onFoldRegionStateChange(region: FoldRegion) = updateImage()
        }
        editor.foldingModel.addListener(foldListener, this)
        val myMarkupModelListener = object : MarkupModelListener {
            override fun afterAdded(highlighter: RangeHighlighterEx) =
                if (EditorUtil.attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme))) updateImage() else Unit

            override fun attributesChanged(highlighter: RangeHighlighterEx,
                renderersChanged: Boolean, fontStyleChanged: Boolean, foregroundColorChanged: Boolean
            ) = if(renderersChanged || foregroundColorChanged)updateImage() else Unit
        }
        editor.filteredDocumentMarkupModel.addMarkupModelListener(this, myMarkupModelListener)
        editor.markupModel.addMarkupModelListener(this, myMarkupModelListener)
        updateTask = object :ReadTask() {
            override fun onCanceled(indicator: ProgressIndicator) {
                renderLock.release()
                renderLock.clean()
                updateImageSoon()
            }

            override fun computeInReadAction(indicator: ProgressIndicator) {
                val map = getOrCreateMap()
                try {
                    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
                    val folds = Folds(editor.foldingModel.allFoldRegions)
                    val hl = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, project, file.virtualFile)
                    map.update(editor, folds,hl)
                    scrollState.computeDimensions(editor, config)
                    ApplicationManager.getApplication().invokeLater {
                        scrollState.recomputeVisible(editor.scrollingModel.visibleArea)
                        repaint()
                    }
                }finally {
                    renderLock.release()
                    if (renderLock.dirty) {
                        renderLock.clean()
                        updateImageSoon()
                    }
                }
            }
        }
        refresh()
    }

    override fun paintVcs(g: Graphics2D) {
        val foldRegions = editor.foldingModel.allFoldRegions.filter { fold -> fold !is CustomFoldRegionImpl && !fold.isExpanded }
        trackerManager.getLineStatusTracker(editor.document)?.getRanges()?.forEach {
            if (it !is LocalRange || it.changelistId == changeListManager.defaultChangeList.id) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
                g.color = LineStatusMarkerDrawUtil.getGutterColor(it.type, editor)
                val documentLine = getDocumentRenderLine(it.line1,it.line2)
                var visualLine1 = EditorUtil.logicalToVisualLine(editor, it.line1)
                var visualLine2 = EditorUtil.logicalToVisualLine(editor, it.line2)
                foldRegions.forEach{ fold->
                    if(editor.document.getLineNumber(fold.startOffset) < it.line1 &&
                        it.line2 <= editor.document.getLineNumber(fold.endOffset)
                    ) visualLine2 = visualLine1 + 1
                }
                if(it.line1 != it.line2 && visualLine1 == visualLine2){
                    val realLine1 = editor.visualToLogicalPosition(VisualPosition(visualLine1,0)).line
                    val realLine2 = editor.visualToLogicalPosition(VisualPosition(visualLine2,0)).line
                    visualLine1 += it.line1 - realLine1
                    visualLine2 += it.line2 - realLine2
                }
                val start = (visualLine1+documentLine.first+1) * config.pixelsPerLine - scrollState.visibleStart
                val end = (visualLine2+documentLine.second+1) * config.pixelsPerLine - scrollState.visibleStart
                g.fillRect(0, start, width, config.pixelsPerLine)
                g.fillRect(0, end, 0, config.pixelsPerLine)
                g.fillRect(0, start + config.pixelsPerLine, width, end - start - config.pixelsPerLine)
            }
        }
    }

    override fun paintSelection(g: Graphics2D, startByte: Int, endByte: Int) {
        val start = editor.offsetToVisualPosition(startByte)
        val end = editor.offsetToVisualPosition(endByte)
        val documentLine = getDocumentRenderLine(editor.document.getLineNumber(startByte),editor.document.getLineNumber(endByte))

        val sX = start.column
        val sY = (start.line + documentLine.first + 1) * config.pixelsPerLine - scrollState.visibleStart
        val eX = end.column
        val eY = (end.line + documentLine.second + 1) * config.pixelsPerLine - scrollState.visibleStart

        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
        g.color = editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)

        // Single line is real easy
        if (start.line == end.line) {
            g.fillRect(
                sX,
                sY,
                eX - sX,
                config.pixelsPerLine
            )
        } else {
            // Draw the line leading in
            g.fillRect(sX, sY, width - sX, config.pixelsPerLine)

            // Then the line at the end
            g.fillRect(0, eY, eX, config.pixelsPerLine)

            if (eY + config.pixelsPerLine != sY) {
                // And if there is anything in between, fill it in
                g.fillRect(0, sY + config.pixelsPerLine, width, eY - sY - config.pixelsPerLine)
            }
        }
    }

    // the minimap is held by a soft reference so the GC can delete it at any time.
    // if its been deleted and we want it again (active tab) we recreate it.
    override fun getOrCreateMap() : OldMinimap {
        var map = mapRef.get()
        if (map == null) {
            map = OldMinimap(config)
            mapRef = SoftReference(map)
        }
        return map
    }
}