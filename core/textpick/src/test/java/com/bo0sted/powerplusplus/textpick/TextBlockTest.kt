package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextBlockTest {

    @Test
    fun containerSummaryIsDroppedInFavourOfItsChildren() {
        val row = TextBlock("Alice, Hello", IntRect(0, 0, 1000, 200))
        val name = TextBlock("Alice", IntRect(20, 20, 300, 80))
        val message = TextBlock("Hello", IntRect(20, 100, 600, 160))

        assertEquals(listOf(name, message), normalizeTextBlocks(listOf(row, message, name)))
    }

    @Test
    fun duplicatesBlanksAndEmptyBoundsAreRemovedAndOrderIsReadingOrder() {
        val right = TextBlock("B", IntRect(500, 10, 600, 50))
        val left = TextBlock("A", IntRect(10, 10, 100, 50))
        val below = TextBlock("C", IntRect(10, 100, 100, 150))
        val raw = listOf(below, right, left, left, TextBlock(" ", IntRect(0, 300, 10, 310)), TextBlock("D", IntRect(5, 5, 5, 50)))

        assertEquals(listOf(left, right, below), normalizeTextBlocks(raw))
    }

    @Test
    fun hitTestPrefersSmallestAndHonoursSlop() {
        val big = TextBlock("big", IntRect(0, 0, 400, 400))
        val small = TextBlock("small", IntRect(100, 100, 150, 150))
        val blocks = listOf(big, small)

        assertEquals(1, hitTest(blocks, 120, 120, slopPx = 0))
        assertEquals(0, hitTest(blocks, 300, 300, slopPx = 0))
        assertEquals(0, hitTest(blocks, 405, 10, slopPx = 10))
        assertNull(hitTest(blocks, 500, 500, slopPx = 10))
    }
}
