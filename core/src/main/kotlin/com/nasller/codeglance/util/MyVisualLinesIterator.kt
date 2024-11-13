package com.nasller.codeglance.util

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.SoftWrap
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.InlayModelImpl

class MyVisualLinesIterator(private val myEditor: EditorImpl, startVisualLine: Int){
	private val myDocument = myEditor.document
	private val myFoldRegions = myEditor.foldingModel.fetchTopLevel() ?: FoldRegion.EMPTY_ARRAY
	private val mySoftWraps = myEditor.softWrapModel.registeredSoftWraps.toList()

	private val myInlaysAbove = ArrayList<Inlay<*>>()
	private var myInlaySet = false

	private var myLocation = Location(startVisualLine)
	private var myNextLocation: Location? = null

	fun atEnd(): Boolean {
		return myLocation.atEnd()
	}

	fun advance() {
		checkEnd()
		if (myNextLocation == null) {
			myLocation.advance()
		} else {
			myLocation = myNextLocation!!
			myNextLocation = null
		}
		myInlaySet = false
	}

	fun getVisualLine(): Int {
		checkEnd()
		return myLocation.visualLine
	}

	fun getVisualLineStartOffset(): Int {
		checkEnd()
		return myLocation.offset
	}

	fun getVisualLineEndOffset(): Int {
		checkEnd()
		setNextLocation()
		return if (myNextLocation!!.atEnd()) myDocument.textLength
		else if (myNextLocation!!.softWrap == myLocation.softWrap) myDocument.getLineEndOffset(myNextLocation!!.logicalLine - 2)
		else myNextLocation!!.offset
	}

	fun getStartFoldingIndex(): Int {
		checkEnd()
		return myLocation.foldRegion
	}

	fun getStartsWithSoftWrap(): SoftWrap? {
		checkEnd()
		if(myLocation.softWrap > 0 && myLocation.softWrap <= mySoftWraps.size){
			val softWrap = mySoftWraps[myLocation.softWrap - 1]
			return if(softWrap.start == myLocation.offset) softWrap else null
		}
		return null
	}

	fun getBlockInlaysAbove(): List<Inlay<*>> {
		checkEnd()
		setInlays()
		return myInlaysAbove
	}

	fun getCurrentFoldRegion(): FoldRegion? {
		checkEnd()
		val foldIndex = myLocation.foldRegion
		if (foldIndex < myFoldRegions.size) {
			return myFoldRegions[foldIndex]
		}
		return null
	}

	fun getFoldRegion(foldIndex: Int): FoldRegion? {
		checkEnd()
		if (foldIndex < myFoldRegions.size) {
			return myFoldRegions[foldIndex]
		}
		return null
	}

	private fun checkEnd() {
		check(!atEnd()) { "Iteration finished" }
	}

	private fun setNextLocation() {
		if (myNextLocation == null) {
			myNextLocation = myLocation.clone()
			myNextLocation!!.advance()
		}
	}

	private fun setInlays() {
		if (myInlaySet) return
		myInlaySet = true
		myInlaysAbove.clear()
		setNextLocation()
		val inlays = myEditor.inlayModel
			.getBlockElementsInRange(myLocation.offset, if (myNextLocation!!.atEnd()) myDocument.textLength else myNextLocation!!.offset - 1)
		for (inlay in inlays) {
			val inlayOffset = inlay.offset - if (inlay.isRelatedToPrecedingText) 0 else 1
			var foldIndex = myLocation.foldRegion
			while (foldIndex < myFoldRegions.size && myFoldRegions[foldIndex].endOffset <= inlayOffset) foldIndex++
			if (foldIndex < myFoldRegions.size && myFoldRegions[foldIndex].startOffset <= inlayOffset && !InlayModelImpl.showWhenFolded(inlay)) continue
			if (inlay.placement == Inlay.Placement.ABOVE_LINE) myInlaysAbove.add(inlay)
		}
	}

	private inner class Location(startVisualLine: Int) : Cloneable {
		var visualLine = 0 // current visual line
		var offset = 0 // start offset of the current visual line
		var logicalLine = 1 // 1 + start logical line of the current visual line
		var foldRegion = 0 // index of the first folding region on current or following visual lines
		var softWrap = 0 // index of the first soft wrap after the start of the current visual line

		init {
			if (startVisualLine < 0 || startVisualLine >= myEditor.visibleLineCount) {
				offset = -1
			} else if (startVisualLine > 0) {
				visualLine = startVisualLine
				offset = myEditor.visualLineStartOffset(startVisualLine)
				logicalLine = myDocument.getLineNumber(offset) + 1
				softWrap = myEditor.softWrapModel.getSoftWrapIndex(offset) + 1
				if (softWrap <= 0) {
					softWrap = -softWrap
				}
				foldRegion = myEditor.foldingModel.getLastCollapsedRegionBefore(offset) + 1
			}
		}

		fun advance() {
			val nextWrapOffset = if (softWrap < mySoftWraps.size) mySoftWraps[softWrap].start else Int.MAX_VALUE
			offset = getNextVisualLineStartOffset(nextWrapOffset)
			if (offset == Int.MAX_VALUE) {
				offset = -1
			} else if (offset == nextWrapOffset) {
				softWrap++
			}
			visualLine++
			while (foldRegion < myFoldRegions.size && myFoldRegions[foldRegion].startOffset < offset) foldRegion++
		}

		private fun getNextVisualLineStartOffset(nextWrapOffset: Int): Int {
			while (logicalLine < myDocument.lineCount) {
				val lineStartOffset = myDocument.getLineStartOffset(logicalLine)
				if (lineStartOffset > nextWrapOffset) return nextWrapOffset
				logicalLine++
				if (!isCollapsed(lineStartOffset)) return lineStartOffset
			}
			return nextWrapOffset
		}

		private fun isCollapsed(offset: Int): Boolean {
			while (foldRegion < myFoldRegions.size) {
				val region = myFoldRegions[foldRegion]
				if (offset <= region.startOffset) return false
				if (offset <= region.endOffset) return true
				foldRegion++
			}
			return false
		}

		fun atEnd(): Boolean {
			return offset == -1
		}

		override fun clone(): Location {
			return try {
				super.clone() as Location
			} catch (e: CloneNotSupportedException) {
				throw RuntimeException(e)
			}
		}
	}
}