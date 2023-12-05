package com.nasller.codeglance.panel.scroll

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.nasller.codeglance.panel.GlancePanel.Companion.fitLineToEditor
import com.nasller.codeglance.panel.scroll.ScrollBar.Companion.PREVIEW_LINES
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

class CustomEditorFragmentRenderer(private val myEditor:EditorImpl){
	private var myVisualLine = 0
	private var myStartVisualLine = 0
	private var myEndVisualLine = 0
	private var isDirty = false
	private var myEditorPreviewHint: LightweightHint? = null

	fun getEditorPreviewHint(): LightweightHint? {
		return myEditorPreviewHint
	}

	private fun update(visualLine: Int) {
		myVisualLine = visualLine
		if (myVisualLine == -1) return
		val oldStartLine = myStartVisualLine
		val oldEndLine = myEndVisualLine
		myStartVisualLine = fitLineToEditor(myEditor, myVisualLine - PREVIEW_LINES)
		myEndVisualLine = fitLineToEditor(myEditor, myVisualLine + PREVIEW_LINES)
		isDirty = isDirty or (oldStartLine != myStartVisualLine || oldEndLine != myEndVisualLine)
	}

	private fun showEditorHint(point: Point, hintInfo: HintHint) {
		val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_MOUSEOVER or
				HintManager.HIDE_BY_ESCAPE or HintManager.HIDE_BY_SCROLLING
		HintManagerImpl.getInstanceImpl().showEditorHint(myEditorPreviewHint!!, myEditor, point, flags, 0, false, hintInfo)
	}

	fun show(visualLine: Int, rangeHighlighters: MutableList<RangeHighlighterEx>, hintInfo: HintHint) {
		val scrollBar = myEditor.scrollPane.verticalScrollBar
		val rootPane = myEditor.component.rootPane ?: SwingUtilities.getWindowAncestor(scrollBar) ?: return
		update(visualLine)
		rangeHighlighters.sortWith { ex1: RangeHighlighterEx, ex2: RangeHighlighterEx ->
			val startPos1 = myEditor.offsetToLogicalPosition(ex1.affectedAreaStartOffset)
			val startPos2 = myEditor.offsetToLogicalPosition(ex2.affectedAreaStartOffset)
			if (startPos1.line != startPos2.line) return@sortWith 0
			startPos1.column - startPos2.column
		}
		val contentInsets = JBUIScale.scale(2) // BalloonPopupBuilderImpl.myContentInsets
		if (myEditorPreviewHint == null) {
			val editorFragmentPreviewPanel = EditorFragmentPreviewPanel(contentInsets, rangeHighlighters)
			editorFragmentPreviewPanel.putClientProperty(BalloonImpl.FORCED_NO_SHADOW, true)
			myEditorPreviewHint = LightweightHint(editorFragmentPreviewPanel)
			myEditorPreviewHint!!.setForceLightweightPopup(true)
		}
		hintInfo.setTextBg(myEditor.backgroundColor)
		val borderColor = myEditor.colorsScheme.getAttributes(EditorColors.CODE_LENS_BORDER_COLOR).effectColor
		hintInfo.borderColor = borderColor ?: myEditor.colorsScheme.defaultForeground
		val point = SwingUtilities.convertPoint(scrollBar, Point(hintInfo.originalPoint), rootPane)
		showEditorHint(point, hintInfo)
	}

	fun clearHint() {
		myEditorPreviewHint = null
	}

	fun hideHint() {
		myEditorPreviewHint?.hide()
		myEditorPreviewHint = null
	}

	private inner class EditorFragmentPreviewPanel(private val myContentInsets:Int,
	                                               private val myHighlighters:MutableList<RangeHighlighterEx>):JPanel() {

		private var myCacheLevel1: BufferedImage? = null
		private var myCacheLevel2: BufferedImage? = null
		private var myCacheFromY = 0
		private var myCacheToY = 0

		@DirtyUI
		override fun getPreferredSize(): Dimension {
			var width = (myEditor.gutterComponentEx.width + myEditor.scrollingModel.visibleArea.width
					- myEditor.scrollPane.verticalScrollBar.width)
			width -= JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) * 2 + myContentInsets
			return Dimension(width - BalloonImpl.POINTER_LENGTH.get(),
				min(2 * PREVIEW_LINES * myEditor.lineHeight, myEditor.visualLineToY(myEndVisualLine) - myEditor.visualLineToY(myStartVisualLine)))
		}

		@DirtyUI
		override fun paintComponent(g: Graphics) {
			if (myVisualLine == -1 || myEditor.isDisposed) return
			val size = preferredSize
			if (size.width <= 0 || size.height <= 0) return
			val gutter: EditorGutterComponentEx = myEditor.gutterComponentEx
			val content = myEditor.contentComponent
			val gutterWidth = gutter.width
			val lineHeight = myEditor.lineHeight
			if (myCacheLevel2 != null && (myEditor.visualLineToY(myStartVisualLine) < myCacheFromY ||
						myEditor.visualLineToY(myEndVisualLine) + lineHeight > myCacheToY)
			) myCacheLevel2 = null
			if (myCacheLevel2 == null) {
				myCacheFromY = max(0, myEditor.visualLineToY(myVisualLine) - CACHE_PREVIEW_LINES * lineHeight)
				myCacheToY = min(myEditor.visualLineToY(myEditor.visibleLineCount), myCacheFromY + (2 * CACHE_PREVIEW_LINES + 1) * lineHeight)
				myCacheLevel2 = ImageUtil.createImage(g, size.width, myCacheToY - myCacheFromY, BufferedImage.TYPE_INT_RGB)
				val cg = myCacheLevel2!!.createGraphics()
				val t = cg.transform
				EditorUIUtil.setupAntialiasing(cg)
				val lineShift = -myCacheFromY
				val shift = JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) + myContentInsets
				val gutterAT = AffineTransform.getTranslateInstance(-shift.toDouble(), lineShift.toDouble())
				val contentAT = AffineTransform.getTranslateInstance((gutterWidth - shift).toDouble(), lineShift.toDouble())
				gutterAT.preConcatenate(t)
				contentAT.preConcatenate(t)
				EditorTextField.SUPPLEMENTARY_KEY[myEditor] = true
				try {
					cg.transform = gutterAT
					cg.setClip(0, -lineShift, gutterWidth, myCacheLevel2!!.height)
					gutter.paint(cg)
					cg.transform = contentAT
					cg.setClip(0, -lineShift, content.width, myCacheLevel2!!.height)
					content.paint(cg)
				} finally {
					EditorTextField.SUPPLEMENTARY_KEY[myEditor] = null
				}
			}
			if (myCacheLevel1 == null) {
				myCacheLevel1 = ImageUtil.createImage(g, size.width, lineHeight * (2 * PREVIEW_LINES + 1), BufferedImage.TYPE_INT_RGB)
				isDirty = true
			}
			if (isDirty) {
				val g2d = myCacheLevel1!!.createGraphics()
				val transform = g2d.transform
				EditorUIUtil.setupAntialiasing(g2d)
				GraphicsUtil.setupAAPainting(g2d)
				g2d.color = myEditor.backgroundColor
				g2d.fillRect(0, 0, width, height)
				val topDisplayedY = max(myEditor.visualLineToY(myStartVisualLine), myEditor.visualLineToY(myVisualLine) - PREVIEW_LINES * lineHeight)
				val translateInstance = AffineTransform.getTranslateInstance(gutterWidth.toDouble(), (myCacheFromY - topDisplayedY).toDouble())
				translateInstance.preConcatenate(transform)
				g2d.transform = translateInstance
				UIUtil.drawImage(g2d, myCacheLevel2!!, -gutterWidth, 0, null)
				val rightEdges = Int2IntOpenHashMap()
				val h = lineHeight - 2
				val colorsScheme = myEditor.colorsScheme
				val font = UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.PLAIN))
				g2d.font = font.deriveFont(font.size * .8f)
				for (ex in myHighlighters) {
					if (!ex.isValid) continue
					val hEndOffset = ex.affectedAreaEndOffset
					val tooltip = ex.errorStripeTooltip ?: continue
					var s = if (tooltip is HighlightInfo) tooltip.description else tooltip.toString()
					if (StringUtil.isEmpty(s)) continue
					s = s.replace("&nbsp;".toRegex(), " ").replace("\\s+".toRegex(), " ")
					s = StringUtil.unescapeXmlEntities(s)
					var logicalPosition = myEditor.offsetToLogicalPosition(hEndOffset)
					val endOfLineOffset = myEditor.document.getLineEndOffset(logicalPosition.line)
					logicalPosition = myEditor.offsetToLogicalPosition(endOfLineOffset)
					val placeToShow = myEditor.logicalPositionToXY(logicalPosition)
					logicalPosition = myEditor.xyToLogicalPosition(placeToShow) //wraps&folding workaround
					placeToShow.x += R * 3 / 2
					placeToShow.y -= myCacheFromY - 1
					val w = g2d.fontMetrics.stringWidth(s)
					var rightEdge = rightEdges[logicalPosition.line]
					placeToShow.x = max(placeToShow.x, rightEdge)
					rightEdge = max(rightEdge, placeToShow.x + w + 3 * R)
					rightEdges.put(logicalPosition.line, rightEdge)
					g2d.color = MessageType.WARNING.popupBackground
					g2d.fillRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R)
					g2d.color = JBColor(JBColor.GRAY, Gray._200)
					g2d.drawRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R)
					g2d.color = JBColor.foreground()
					g2d.drawString(s, placeToShow.x + R, placeToShow.y + h - g2d.getFontMetrics(g2d.font).descent / 2 - 2)
				}
				isDirty = false
			}
			val g2 = g.create() as Graphics2D
			try {
				GraphicsUtil.setupAAPainting(g2)
				g2.clip = RoundRectangle2D.Double(0.0, 0.0, size.width - .5, size.height - .5, 2.0, 2.0)
				UIUtil.drawImage(g2, myCacheLevel1!!, 0, 0, this)
				if (StartupUiUtil.isUnderDarcula && !NewUiValue.isEnabled()) {
					//Add glass effect
					val s = Rectangle(0, 0, size.width, size.height)
					val cx = size.width / 2.0
					val rx = size.width / 10.0
					val ry = lineHeight * 3 / 2
					g2.paint = GradientPaint(0f, 0f, Gray._255.withAlpha(75), 0f, ry.toFloat(), Gray._255.withAlpha(10))
					val pseudoMajorAxis = size.width - rx * 9 / 5
					val cy = 0.0
					val topShape1 = Ellipse2D.Double(cx - rx - pseudoMajorAxis / 2, cy - ry, 2 * rx, (2 * ry).toDouble())
					val topShape2 = Ellipse2D.Double(cx - rx + pseudoMajorAxis / 2, cy - ry, 2 * rx, (2 * ry).toDouble())
					val topArea = Area(topShape1)
					topArea.add(Area(topShape2))
					topArea.add(Area(Rectangle2D.Double(cx - pseudoMajorAxis / 2, cy, pseudoMajorAxis, ry.toDouble())))
					g2.fill(topArea)
					val bottomArea = Area(s)
					bottomArea.subtract(topArea)
					g2.paint = GradientPaint(0f, (size.height - ry).toFloat(), Gray._0.withAlpha(10), 0f, size.height.toFloat(), Gray._255.withAlpha(30))
					g2.fill(bottomArea)
				}
			} finally {
				g2.dispose()
			}
		}
	}

	companion object{
		const val EDITOR_FRAGMENT_POPUP_BORDER = 1
		private const val CACHE_PREVIEW_LINES = 100
		private const val R = 6
	}
}