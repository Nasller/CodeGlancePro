package com.nasller.codeglance.render

import org.testng.Assert.*
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

/**
 * Some basic sanity tests that the weight generation function works OK.
 */
class CharacterWeightTest {
    @Test
    fun test_lower_boundaries() {
        assertEquals(0.0f, getTopWeight(0), 0.001f)
        assertEquals(0.0f, getTopWeight(1), 0.001f)
        assertEquals(0.0f, getTopWeight(32), 0.001f)
        assertNotEquals(0.0f, getTopWeight(33))
        assertNotEquals(0.0f, getTopWeight(127))
        assertNotEquals(0.0f, getTopWeight(128))
    }

    @Test(dataProvider = "Test-Relative-Weights")
    fun test_relative_weights_are_sane(a: Char, b: Char) {
        assertTrue(getTopWeight(a.toInt()) + getBottomWeight(a.toInt()) < getTopWeight(b.toInt()) + getBottomWeight(b.toInt()))
    }

    @Test
    fun test_known_values() {
        assertEquals(0.2458f, getTopWeight('v'.toInt()))
        assertEquals(0.3538f, getBottomWeight('v'.toInt()))
    }

    companion object {

        @DataProvider(name = "Test-Relative-Weights")
        fun testRelativeWeights(): Array<Array<Any>> {
            return arrayOf(
                arrayOf<Any>('.', ','),
                arrayOf<Any>('1', '8'),
                arrayOf<Any>('.', 'a'),
                arrayOf<Any>(',', '1')
            )
        }
    }
}