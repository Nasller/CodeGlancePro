package com.nasller.codeglance.panel

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LocalRange
import com.nasller.codeglance.CodeGlancePlugin.Companion.isCustomFoldRegionImpl
import com.nasller.codeglance.render.Minimap
import com.nasller.codeglance.util.attributesImpactForegroundColor
import java.awt.Graphics2D
import java.lang.ref.SoftReference

class GlancePanel(project: Project, textEditor: TextEditor) : AbstractGlancePanel<Minimap>(project,textEditor) {
    init {
        Disposer.register(textEditor, this)
        scrollbar = ScrollBar(textEditor, scrollState,this)
        add(scrollbar)
        editor.foldingModel.addListener(object : FoldingListener {
            override fun onFoldRegionStateChange(region: FoldRegion) = updateImage()
        }, this)
        val myMarkupModelListener = object : MarkupModelListener {
            override fun afterAdded(highlighter: RangeHighlighterEx) =
                if (attributesImpactForegroundColor(highlighter.getTextAttributes(editor.colorsScheme))) updateImage() else Unit

            override fun attributesChanged(highlighter: RangeHighlighterEx,
                renderersChanged: Boolean, fontStyleChanged: Boolean, foregroundColorChanged: Boolean
            ) = if(renderersChanged || foregroundColorChanged)updateImage() else Unit
        }
        editor.filteredDocumentMarkupModel.addMarkupModelListener(this, myMarkupModelListener)
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = repaint()
            override fun caretAdded(event: CaretEvent) = repaint()
            override fun caretRemoved(event: CaretEvent) = repaint()
        },this)
        refresh()
    }

    override fun computeInReadAction(indicator: ProgressIndicator) {
        val map = getOrCreateMap()
        try {
            map.update(scrollState,indicator)
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

    override fun paintVcs(g: Graphics2D) {
        val foldRegions = editor.foldingModel.allFoldRegions.filter { fold -> !isCustomFoldRegionImpl(fold) && !fold.isExpanded }
        trackerManager.getLineStatusTracker(editor.document)?.getRanges()?.run {
            g.composite = srcOver0_4
            forEach{
                if (it !is LocalRange || it.changelistId == changeListManager.defaultChangeList.id) {
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
                    val start = (visualLine1+documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
                    val end = (visualLine2+documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
                    g.fillRect(0, start, width, config.pixelsPerLine)
                    g.fillRect(0, end, 0, config.pixelsPerLine)
                    g.fillRect(0, start + config.pixelsPerLine, width, end - start - config.pixelsPerLine)
                }
            }
        }
    }

    override fun paintErrorStripes(g: Graphics2D) {
        g.composite = srcOver1
        editor.filteredDocumentMarkupModel.allHighlighters.forEach {
            val info = HighlightInfo.fromRangeHighlighter(it) ?: return
            if (info.severity.myVal > HighlightSeverity.INFORMATION.myVal) {
                val textAttributes = it.getTextAttributes(editor.colorsScheme)
                if (textAttributes != null) {
                    g.color = textAttributes.errorStripeColor?:textAttributes.effectColor?:textAttributes.backgroundColor?:textAttributes.foregroundColor
                    val documentLine = getDocumentRenderLine(editor.document.getLineNumber(it.startOffset), editor.document.getLineNumber(it.endOffset))
                    val visualLine1 = editor.offsetToVisualLine(it.startOffset, false)
                    val visualLine2 = editor.offsetToVisualLine(it.endOffset, false)
                    val start = (visualLine1 + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
                    val end = (visualLine2 + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart
                    val width = width / 3
                    g.fillRect(0, start, width, config.pixelsPerLine)
                    g.fillRect(0, end, 0, config.pixelsPerLine)
                    g.fillRect(0, start + config.pixelsPerLine, width, end - start - config.pixelsPerLine)
                }
            }
        }
    }

    override fun paintCaretPosition(g: Graphics2D) {
        g.composite = srcOver1
        editor.caretModel.allCarets.forEach{
            g.color = editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
            val documentLine = getDocumentRenderLine(it.logicalPosition.line,it.logicalPosition.line)
            val start = (it.visualPosition.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
            val end = (it.visualPosition.line + documentLine.second + 1) * config.pixelsPerLine - scrollState.visibleStart
            g.fillRect(0, start, width, config.pixelsPerLine)
            g.fillRect(0, end, 0, config.pixelsPerLine)
            g.fillRect(0, start + config.pixelsPerLine, width, end - start - config.pixelsPerLine)
        }
    }

    override fun paintSelection(g: Graphics2D, startByte: Int, endByte: Int) {
        val start = editor.offsetToVisualPosition(startByte)
        val end = editor.offsetToVisualPosition(endByte)
        val documentLine = getDocumentRenderLine(editor.document.getLineNumber(startByte),editor.document.getLineNumber(endByte))

        val sX = start.column
        val sY = (start.line + documentLine.first) * config.pixelsPerLine - scrollState.visibleStart
        val eX = end.column + 1
        val eY = (end.line + documentLine.second) * config.pixelsPerLine - scrollState.visibleStart

        g.composite = srcOver1
        g.color = editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
        // Single line is real easy
        if (start.line == end.line) {
            g.fillRect(sX, sY, eX - sX, config.pixelsPerLine)
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

    override fun getOrCreateMap() : Minimap {
        var map = mapRef.get()
        if (map == null) {
            map = Minimap(config,this)
            mapRef = SoftReference(map)
        }
        return map
    }
}