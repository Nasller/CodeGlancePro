package com.nasller.codeglance.render

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.CustomFoldRegionImpl

// Is a copy of Array<FoldRegion> that only contains folded folds and can be passed safely to another thread
class Folds{
    private val foldsSet: HashSet<Int> = hashSetOf()
//    val customFolds = hashMapOf<Int, CustomFoldRegion>()

    constructor(allFolds: Array<FoldRegion>) {
        allFolds
            .filterNot { it.isExpanded || it is CustomFoldRegionImpl }
            .forEach { foldRegion ->
//                if(foldRegion is CustomFoldRegion) customFolds[foldRegion.startOffset] = foldRegion
                for (index in foldRegion.startOffset until foldRegion.endOffset)
                    foldsSet.add(index)
            }
    }

    // Used by tests that want an empty fold set
    constructor()

    /**
     * Checks if a given position is within a folded region
     * @param position  the offset from the start of file in chars
     * *
     * @return true if the given position is folded.
     */
    fun isFolded(position: Int): Boolean {
        return foldsSet.contains(position)
    }
}