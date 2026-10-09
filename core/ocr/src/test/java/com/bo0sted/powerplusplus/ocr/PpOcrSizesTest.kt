package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class PpOcrSizesTest {

    @Test
    fun detectionKeepsLargeImagesAndRoundsSidesHalfToEven() {
        // 2992 / 32 = 93.5 rounds to 94 (Python's round), so a Pixel 9 Pro XL screen runs at 1344×3008.
        assertEquals(1344 to 3008, PpOcr.detectionSize(1344, 2992))
        // 2960 / 32 = 92.5 rounds to 92.
        assertEquals(320 to 2944, PpOcr.detectionSize(320, 2960))
    }

    @Test
    fun detectionScalesTheShortSideUpTo64AndTheLongSideDownTo4000() {
        assertEquals(64 to 640, PpOcr.detectionSize(32, 320))
        assertEquals(4000 to 64, PpOcr.detectionSize(8000, 128))
    }

    @Test
    fun recognitionPadsToAtLeast320AndCapsAt3200() {
        assertEquals(96 to 320, PpOcr.recognitionWidths(40, 20))
        assertEquals(960 to 960, PpOcr.recognitionWidths(400, 20))
        assertEquals(3200 to 3200, PpOcr.recognitionWidths(2000, 20))
    }
}
