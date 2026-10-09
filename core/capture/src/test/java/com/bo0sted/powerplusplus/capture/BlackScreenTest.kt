package com.bo0sted.powerplusplus.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackScreenTest {

    private val black = 0xFF000000.toInt()

    @Test
    fun blackBodyWithVisibleSystemBarsIsBlocked() {
        // White status bar (top 5 %) and navigation bar (bottom 5 %) around a black app.
        assertTrue(isSolidBlack(1000, 2000) { _, y -> if (y < 100 || y >= 1900) -1 else black })
    }

    @Test
    fun darkThemeWithAnyContentIsNotBlocked() {
        assertFalse(isSolidBlack(1000, 2000) { x, y -> if (x in 300..700 && y in 900..960) 0xFFE0E0E0.toInt() else black })
    }
}
