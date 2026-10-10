package com.bo0sted.powerplusplus.media

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MediaScanTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val dark = 0xFF121212.toInt()
    private val card = 0xFFEEEEEE.toInt()

    /** A screen-like canvas, already at the scan's working size. */
    private class Canvas(val width: Int, val height: Int, background: Int) {
        val pixels = IntArray(width * height) { background }

        fun fill(r: IntRect, color: Int) {
            for (y in r.top until r.bottom) for (x in r.left until r.right) pixels[y * width + x] = color
        }

        /** Photo-like content: every pixel a different, random colour. */
        fun photo(r: IntRect, seed: Int = 1) {
            val random = Random(seed)
            for (y in r.top until r.bottom) for (x in r.left until r.right) pixels[y * width + x] = 0xFF000000.toInt() or random.nextInt(0xFFFFFF)
        }

        /** Text-like marks: small dark blocks with gaps, like letters. */
        fun text(r: IntRect, color: Int) {
            for (y in r.top until r.bottom step 6) for (x in r.left until r.right step 5) fill(IntRect(x, y, minOf(x + 3, r.right), minOf(y + 4, r.bottom)), color)
        }

        fun scan(minSide: Int = 18) = MediaScan.scanPixels(pixels, width, height, minSide)
    }

    @Test
    fun findsAPhotoOnAPlainBackground() {
        val screen = Canvas(200, 400, white)
        val photo = IntRect(20, 100, 180, 220)
        screen.photo(photo)
        screen.text(IntRect(20, 240, 180, 270), black)

        assertEquals(listOf(photo), screen.scan())
    }

    @Test
    fun findsBothACardAndThePhotoInsideIt() {
        val screen = Canvas(200, 400, white)
        val cardBounds = IntRect(10, 50, 190, 330)
        screen.fill(cardBounds, card)
        screen.text(IntRect(20, 60, 120, 80), black)
        val photo = IntRect(20, 100, 180, 260)
        screen.photo(photo)
        screen.text(IntRect(20, 280, 180, 320), black)

        val found = screen.scan()
        assertTrue(found.toString(), photo in found)
        assertTrue(found.toString(), cardBounds in found)
    }

    @Test
    fun aPlainButtonOrTextOnlyCardIsNotMedia() {
        val screen = Canvas(200, 400, white)
        val button = IntRect(20, 100, 180, 160)
        screen.fill(button, 0xFF3366CC.toInt())
        screen.text(IntRect(40, 120, 160, 140), white)
        val textCard = IntRect(10, 200, 190, 330)
        screen.fill(textCard, card)
        screen.text(IntRect(20, 210, 180, 320), black)

        assertEquals(emptyList<IntRect>(), screen.scan())
    }

    @Test
    fun findsTheFrameOfALetterboxedVideo() {
        val screen = Canvas(200, 400, white)
        val player = IntRect(0, 100, 200, 250)
        screen.fill(player, black)
        val frame = IntRect(0, 130, 200, 220)
        screen.photo(frame)

        assertTrue(screen.scan().toString(), frame in screen.scan())
    }

    @Test
    fun aVideoOnABlackPageIsFoundWithoutItsBars() {
        val screen = Canvas(200, 400, dark)
        val frame = IntRect(0, 130, 200, 220)
        screen.photo(frame)

        assertEquals(listOf(frame), screen.scan())
    }

    @Test
    fun aDividerTouchingAPhotoDoesNotWidenIt() {
        val screen = Canvas(200, 400, white)
        screen.fill(IntRect(0, 99, 200, 100), 0xFFDDDDDD.toInt())
        val photo = IntRect(40, 100, 160, 200)
        screen.photo(photo)

        assertEquals(listOf(photo), screen.scan())
    }

    @Test
    fun aFullScreenPhotoIsTheWholeScreen() {
        val screen = Canvas(100, 200, white)
        screen.photo(IntRect(0, 0, 100, 200))

        assertEquals(listOf(IntRect(0, 0, 100, 200)), screen.scan())
    }

    @Test
    fun smallImagesLikeIconsAreIgnored() {
        val screen = Canvas(200, 400, white)
        screen.photo(IntRect(20, 20, 34, 34))

        assertEquals(emptyList<IntRect>(), screen.scan())
    }

    @Test
    fun edgesSnapToTheStrongestNearbyContrast() {
        val canvas = Canvas(100, 100, white)
        val photo = IntRect(21, 23, 77, 79)
        canvas.photo(photo)
        val pixel = { x: Int, y: Int -> canvas.pixels[y * 100 + x] }

        assertEquals(photo, MediaScan.snapEdges(IntRect(20, 20, 80, 80), 100, 100, 4, pixel))
    }
}
