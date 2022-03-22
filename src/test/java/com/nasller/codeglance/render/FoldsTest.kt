package com.nasller.codeglance.render

import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class FoldsTest {
    @Test
    fun testNothingMatchesEmptyFoldSet() {
        val folds = Folds()

        assertFalse(folds.isFolded(0))
        assertFalse(folds.isFolded(-1))
        assertFalse(folds.isFolded(1))
        assertFalse(folds.isFolded(99))
    }

    @Test
    fun testFoldedRegionMatch() {
        val folds = Folds(arrayOf(FakeFold(10, 20, true)))

        assertFalse(folds.isFolded(0))
        assertTrue(folds.isFolded(10))
        assertTrue(folds.isFolded(15))
        assertFalse(folds.isFolded(20))
        assertFalse(folds.isFolded(25))
    }


    @Test
    fun testUnfoldedRegionsDontMatch() {
        val folds = Folds(arrayOf(FakeFold(10, 20, false)))

        assertFalse(folds.isFolded(0))
        assertFalse(folds.isFolded(10))
        assertFalse(folds.isFolded(15))
        assertFalse(folds.isFolded(25))
    }

    @Test
    fun testNestedFoldedRegions() {
        val folds = Folds(
            arrayOf(
                FakeFold(10, 20, true),
                FakeFold(12, 16, true),
                FakeFold(14, 15, true),
                FakeFold(18, 19, true)
            )
        )

        assertFalse(folds.isFolded(0))
        assertTrue(folds.isFolded(11))
        assertTrue(folds.isFolded(13))
        assertTrue(folds.isFolded(14))
        assertTrue(folds.isFolded(18))
        assertFalse(folds.isFolded(20))
        assertFalse(folds.isFolded(25))
    }
}