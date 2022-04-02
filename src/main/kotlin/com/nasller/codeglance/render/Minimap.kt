package com.nasller.codeglance.render

import com.intellij.lexer.Lexer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.tree.IElementType
import com.intellij.util.ui.ImageUtil
import com.nasller.codeglance.config.Config
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.floor

/**
 * A rendered minimap of a document
 */
class Minimap(private val config: Config) {
	var img: BufferedImage? = null
	private var height: Int = 0
	private val logger = Logger.getInstance(javaClass)
	private var lineEndings: ArrayList<Int>? = null

	/**
	 * Scans over the entire document once to work out the required dimensions then rebuilds the image if necessary.
	 * Because java chars are UTF-8 16 bit chars this function should be UTF safe in the 2 byte range, which is all intellij
	 * seems to handle anyway....
	 */
	private fun updateDimensions(text: CharSequence, folds: Folds) {
		var lineLength = 0    // The current line length
		var lines = 1          // The total number of lines in the document
		var last = ' '
		var ch: Char

		val lineEndings1 = ArrayList<Int>()
		// Magical first line
		lineEndings1.add(-1)

		var i = 0
		val len = text.length
		while (i < len) {
			if (folds.isFolded(i)) {
				i++
				continue
			}

			ch = text[i]

			if (ch == '\n' || (ch == '\r' && last != '\n')) {
				lineEndings1.add(i)
				lineLength = 0
				lines++
			} else if (ch == '\t') {
				lineLength += 4
			} else {
				lineLength++
			}

			last = ch
			i++
		}
		// If there is no final newline add one.
		if (lineEndings1[lineEndings1.size - 1] != text.length - 1) lineEndings1.add(text.length - 1)

		this.lineEndings = lineEndings1
		height = (lines + 1) * config.pixelsPerLine

		// If the image is too small to represent the entire document now then regenerate it
		if (img == null || img!!.height < height || img!!.width < config.width) {
			if (img != null) img!!.flush()
			// Create an image that is a bit bigger then the one we need so we don't need to re-create it again soon.
			// Documents can get big, so rather then relative sizes lets just add a fixed amount on.
			img = ImageUtil.createImage(config.width, height + 100 * config.pixelsPerLine, BufferedImage.TYPE_4BYTE_ABGR)

			logger.debug("Created new image")
		}
	}

	/**
	 * Binary search for a line ending.
	 * @param i character offset from start of document
	 * *
	 * @return 3 element array, [line_number, o]
	 */
	private fun getLine(i: Int): LineInfo {
		// We can get called before the line scan has been done. Just return the first line.
		if (lineEndings == null) return NO_LINES
		if (lineEndings!!.size == 0) return NO_LINES
		val lines = lineEndings!![lineEndings!!.size - 1]

		// Dummy entries if there are no lines
		if (lineEndings!!.size == 1) return NO_LINES
		if (lineEndings!!.size == 2) return LineInfo(1, lineEndings!![0] + 1)

		var indexMin = 0
		var indexMax = lineEndings!!.size - 1
		var indexMid: Int
		var value: Int

		val clampedI = clamp(i, 0, lines)

		while (true) {
			indexMid = floor(((indexMin + indexMax) / 2.0f).toDouble()).toInt() // Key space is pretty linear, might be able to use that to scale our next point.
			value = lineEndings!![indexMid]

			if (value < clampedI) {
				if (clampedI < lineEndings!![indexMid + 1]) return LineInfo(indexMid + 1, value + 1)

				indexMin = indexMid + 1
			} else if (clampedI < value) {
				if (lineEndings!![indexMid - 1] < clampedI) return LineInfo(indexMid, lineEndings!![indexMid - 1] + 1)

				indexMax = indexMid - 1
			} else {
				// character at i is actually a newline, so grab the line before it.
				return LineInfo(indexMid, lineEndings!![indexMid - 1] + 1)
			}
		}
	}

	/**
	 * Works out the color a token should be rendered in.
	 */
	private fun getColorForElementType(lexer: Lexer, hl: SyntaxHighlighter, colorScheme: EditorColorsScheme, markupModelEx: MarkupModelEx): Int {
		var color: Color? = null
		val tokenType = lexer.tokenType
		val attributes = hl.getTokenHighlights(tokenType)
		try{
			attributes.asSequence().mapNotNull { colorScheme.getAttributes(it) }
				.forEach { attr -> attr.foregroundColor?.let { color = it } }
			if(color == null || color == colorScheme.defaultForeground) {
				markupModelEx.processRangeHighlightersOverlappingWith(lexer.tokenStart, lexer.tokenEnd) {
					val textAttributes = it.getTextAttributes(colorScheme)
					if (textAttributes != null && textAttributes.foregroundColor != null){
						color = textAttributes.foregroundColor
						false
					}else true
				}
			}
		}catch (e:Exception){
			logger.error(e)
		}
		return color?.rgb?:colorScheme.defaultForeground.rgb
	}

	/**
	 * Internal worker function to update the minimap image
	 */
	@Synchronized
	fun update(editor: EditorEx, folds: Folds,hl:SyntaxHighlighter) {
		logger.debug("Updating file image.")
		val text = editor.document.charsSequence
		val markupModelEx = editor.filteredDocumentMarkupModel
		updateDimensions(text, folds)

		var color: Int
		var ch: Char
		var startLine: LineInfo
		val lexer = hl.highlightingLexer
		var tokenType: IElementType?

		val g = img!!.graphics as Graphics2D
		g.composite = CLEAR
		g.fillRect(0, 0, img!!.width, img!!.height)

		lexer.start(text)
		tokenType = lexer.tokenType

		var x: Int
		var y: Int
		while (tokenType != null) {
			val start = lexer.tokenStart
			startLine = getLine(start)
			y = (startLine.number-1) * config.pixelsPerLine

			color = getColorForElementType(lexer, hl, editor.colorsScheme,markupModelEx)

			// Pre-loop to count whitespace from start of line.
			x = 0
			for (i in startLine.begin until start) {
				// Don't count lines inside folded regions.
				if (folds.isFolded(i)) break

				x += if (text[i] == '\t') {
					4
				} else {
					1
				}

				// Abort if this line is getting to long...
				if (x > config.width) break
			}

			// Render whole token, make sure multi lines are handled gracefully.
			for (i in start until lexer.tokenEnd) {
				// Don't render folds.
				if (folds.isFolded(i)) break
				// Watch out for tokens that extend past the document... bad plugins? see issue #138
				if (i >= text.length) return

				ch = text[i]

				when (ch) {
					'\n' -> {
						x = 0
						y += config.pixelsPerLine
					}
					'\t' -> {
						x += 4
					}
					else -> {
						x += 1
					}
				}

				if (0 <= x && x < img!!.width && 0 <= y && y + config.pixelsPerLine < img!!.height) {
					if (config.clean) {
						renderClean(x, y, text[i].code, color)
					} else {
						renderAccurate(x, y, text[i].code, color)
					}
				}
			}

			lexer.advance()
			tokenType = lexer.tokenType
		}
	}

	private fun renderClean(x: Int, y: Int, char: Int, color: Int) {
		val weight = when (char) {
			in 0..32 -> 0.0f
			in 33..126 -> 0.8f
			else -> 0.4f
		}

		if (weight == 0.0f) return

		when (config.pixelsPerLine) {
			1 -> // Cant show whitespace between lines any more. This looks rather ugly...
				setPixel(x, y + 1, color, weight * 0.6f)

			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, color, weight * 0.3f)
				setPixel(x, y + 1, color, weight * 0.6f)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, color, weight * 0.1f)
				setPixel(x, y + 1, color, weight * 0.6f)
				setPixel(x, y + 2, color, weight * 0.6f)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, color, weight * 0.6f)
				setPixel(x, y + 2, color, weight * 0.6f)
				setPixel(x, y + 3, color, weight * 0.6f)
			}
		}
	}

	private fun renderAccurate(x: Int, y: Int, char: Int, color: Int) {
		val topWeight = getTopWeight(char)
		val bottomWeight = getBottomWeight(char)
		// No point rendering non visible characters.
		if (topWeight == 0.0f && bottomWeight == 0.0f) return

		when (config.pixelsPerLine) {
			1 -> // Cant show whitespace between lines any more. This looks rather ugly...
				setPixel(x, y + 1, color, ((topWeight + bottomWeight) / 2.0).toFloat())

			2 -> {
				// Two lines we make the top line a little lighter to give the illusion of whitespace between lines.
				setPixel(x, y, color, topWeight * 0.5f)
				setPixel(x, y + 1, color, bottomWeight)
			}
			3 -> {
				// Three lines we make the top nearly empty, and fade the bottom a little too
				setPixel(x, y, color, topWeight * 0.3f)
				setPixel(x, y + 1, color, ((topWeight + bottomWeight) / 2.0).toFloat())
				setPixel(x, y + 2, color, bottomWeight * 0.7f)
			}
			4 -> {
				// Empty top line, Nice blend for everything else
				setPixel(x, y + 1, color, topWeight)
				setPixel(x, y + 2, color, ((topWeight + bottomWeight) / 2.0).toFloat())
				setPixel(x, y + 3, color, bottomWeight)
			}
		}
	}

	/**
	 * mask out the alpha component and set it to the given value.
	 * @param color         Color A
	 * *
	 * @param alpha     alpha percent from 0-1.
	 * *
	 * @return int color
	 */
	private fun setPixel(x: Int, y: Int, color: Int, alpha: Float) {
		var a = alpha
		if (a > 1) a = color.toFloat()
		if (a < 0) a = 0f

		// abgr is backwards?
		unpackedColor[3] = (a * 255).toInt()
		unpackedColor[0] = (color and 16711680) shr 16
		unpackedColor[1] = (color and 65280) shr 8
		unpackedColor[2] = (color and 255)

		img!!.raster.setPixel(x, y, unpackedColor)
	}

	class LineInfo internal constructor(var number: Int, var begin: Int)

	companion object {
		private val CLEAR = AlphaComposite.getInstance(AlphaComposite.CLEAR)
		private val unpackedColor = IntArray(4)
		private val NO_LINES = LineInfo(1, 0)

		fun clamp(v: Int, min: Int, max: Int): Int {
			if (v < min) return min
			if (v > max) return max
			return v
		}
	}
}