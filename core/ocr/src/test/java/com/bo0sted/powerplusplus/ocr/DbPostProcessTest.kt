package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class DbPostProcessTest {

    private fun map(width: Int, height: Int, vararg regions: Pair<TextBox, Float>): FloatArray {
        val map = FloatArray(width * height) { 0.05f }
        for ((box, value) in regions) {
            for (y in box.top until box.bottom) for (x in box.left until box.right) map[y * width + x] = value
        }
        return map
    }

    private fun boxes(map: FloatArray, width: Int, height: Int, destWidth: Int = width, destHeight: Int = height, maxCandidates: Int = 3000) =
        DbPostProcess.boxes(map, width, height, destWidth, destHeight, thresh = 0.3f, boxThresh = 0.6f, unclipRatio = 1.5f, maxCandidates = maxCandidates)

    @Test
    fun expandsEachRegionByAreaTimesRatioOverPerimeter() {
        // Pixels 10..29 × 20..25: corner centres span 19 × 5, so d = 19·5·1.5 / (2·24) = 2.97 → 3.
        val map = map(64, 64, TextBox(10, 20, 30, 26) to 0.9f)
        assertEquals(listOf(TextBox(7, 17, 32, 28)), boxes(map, 64, 64))
    }

    @Test
    fun scalesBoxesToTheDestinationAndClampsThem() {
        val map = map(64, 64, TextBox(0, 20, 30, 26) to 0.9f)
        // Left edge -3 is clamped to 0; the rest is doubled.
        assertEquals(listOf(TextBox(0, 34, 64, 56)), boxes(map, 64, 64, destWidth = 128, destHeight = 128))
    }

    @Test
    fun dropsRegionsBelowTheBoxThresholdAndTooThinOnes() {
        val map = map(
            64, 64,
            TextBox(5, 5, 30, 12) to 0.5f, // Above thresh, but its mean is under box_thresh.
            TextBox(5, 20, 30, 23) to 0.9f, // Short side 2 px between pixel centres.
            TextBox(5, 40, 30, 47) to 0.9f,
        )
        assertEquals(1, boxes(map, 64, 64).size)
    }

    @Test
    fun treatsDiagonalNeighboursAsOneRegion() {
        // One region spanning pixels 10..21 (d = 11·11·1.5 / 44 = 4.125); 4-connected, it would end at 19.
        val map = map(64, 64, TextBox(10, 10, 20, 20) to 0.9f, TextBox(20, 20, 22, 22) to 0.9f)
        assertEquals(listOf(TextBox(6, 6, 25, 25)), boxes(map, 64, 64))
    }

    @Test
    fun stopsAtMaxCandidates() {
        val map = map(64, 64, TextBox(5, 5, 20, 12) to 0.9f, TextBox(5, 30, 20, 37) to 0.9f)
        assertEquals(1, boxes(map, 64, 64, maxCandidates = 1).size)
    }
}
