package com.bo0sted.powerplusplus.media

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRegionsTest {

    private val image = IntRect(100, 100, 500, 400)
    private val post = IntRect(50, 50, 550, 700)
    private val other = IntRect(100, 900, 500, 1200)

    @Test
    fun nearDuplicatesAreDroppedKeepingTheFirst() {
        val almost = IntRect(102, 101, 499, 401)
        assertEquals(listOf(image, other), distinctRegions(listOf(image, almost, other)))
    }

    @Test
    fun orderIsRegionsUnderThePointSmallestFirstThenNearest() {
        val far = IntRect(100, 2000, 500, 2300)
        assertEquals(listOf(image, post, other, far), orderedAround(listOf(far, post, other, image), IntOffset(300, 250)))
    }

    @Test
    fun regionAtPicksTheSmallestUnderThePoint() {
        assertEquals(1, regionAt(listOf(post, image), IntOffset(300, 250), slopPx = 0))
        assertEquals(0, regionAt(listOf(post, image), IntOffset(300, 600), slopPx = 0))
        assertNull(regionAt(listOf(post, image), IntOffset(300, 800), slopPx = 0))
    }

    @Test
    fun repeatedTapsStepThroughAndWrapAround() {
        val cycle = RegionCycle()
        val regions = listOf(post, other, image)
        val start = IntRect(250, 200, 350, 300)

        val first = cycle.next(regions, start)
        assertEquals(image, first)
        val second = cycle.next(regions, first!!)
        assertEquals(post, second)
        val third = cycle.next(regions, second!!)
        assertEquals(other, third)
        assertEquals(image, cycle.next(regions, third!!))
    }

    @Test
    fun movingTheSelectionStartsOverFromIt() {
        val cycle = RegionCycle()
        val regions = listOf(post, other, image)
        cycle.next(regions, IntRect(250, 200, 350, 300))

        assertEquals(other, cycle.next(regions, IntRect(200, 1000, 300, 1100)))
    }

    @Test
    fun noRegionsGivesNull() {
        assertNull(RegionCycle().next(emptyList(), image))
    }
}
