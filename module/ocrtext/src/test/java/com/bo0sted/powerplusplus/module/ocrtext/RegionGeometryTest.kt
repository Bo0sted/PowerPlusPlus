package com.bo0sted.powerplusplus.module.ocrtext

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionGeometryTest {

    @Test
    fun roundsToWholePixels() {
        assertEquals(IntRect(100, 200, 301, 260), Rect(100.4f, 199.6f, 300.5f, 259.8f).toRegion(1344, 2992))
    }

    @Test
    fun staysInsideTheImage() {
        assertEquals(IntRect(0, 0, 1344, 2992), Rect(-3f, -0.4f, 1350f, 3000f).toRegion(1344, 2992))
    }
}
