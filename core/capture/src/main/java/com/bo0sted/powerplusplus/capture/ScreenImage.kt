package com.bo0sted.powerplusplus.capture

import android.graphics.Bitmap
import androidx.core.graphics.get

/** Checks on screen captures. Everything here can be slow; call it off the main thread. */
object ScreenImage {

    /** A mutable-free software copy, needed for pixel access and cropping (hardware bitmaps allow neither). */
    fun toSoftware(bitmap: Bitmap): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

    /**
     * Whether the capture looks like a screen-capture-blocked app: the system renders `FLAG_SECURE`
     * windows as solid black. Only the middle 80 % of the height is checked, since the status and
     * navigation bars stay visible around a blocked app.
     */
    fun looksBlocked(bitmap: Bitmap): Boolean = isSolidBlack(bitmap.width, bitmap.height) { x, y -> bitmap[x, y] }
}

/**
 * True if every sampled pixel in the middle 80 % of the height is (near) black. Samples a 48×48
 * grid, which is enough to hit text or icons in any real UI.
 */
internal fun isSolidBlack(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): Boolean {
    if (width <= 0 || height <= 0) return true
    val top = height / 10
    val bottom = height - height / 10
    for (row in 0 until GRID) {
        val y = top + (bottom - top - 1) * row / (GRID - 1)
        for (column in 0 until GRID) {
            val x = (width - 1) * column / (GRID - 1)
            val argb = pixel(x, y)
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            if (r > BLACK_TOLERANCE || g > BLACK_TOLERANCE || b > BLACK_TOLERANCE) return false
        }
    }
    return true
}

private const val GRID = 48
private const val BLACK_TOLERANCE = 2
