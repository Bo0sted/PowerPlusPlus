package com.bo0sted.powerplusplus.module.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import kotlin.math.roundToInt

/** Bitmap work for screenshots. Everything here can be slow; call it off the main thread. */
internal object ScreenshotImage {

    /** A mutable-free software copy, needed for pixel access and cropping (hardware bitmaps allow neither). */
    fun toSoftware(bitmap: Bitmap): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

    /**
     * Whether the capture looks like a screen-capture-blocked app: the system renders `FLAG_SECURE`
     * windows as solid black. Only the middle 80 % of the height is checked, since the status and
     * navigation bars stay visible around a blocked app.
     */
    fun looksBlocked(bitmap: Bitmap): Boolean = isSolidBlack(bitmap.width, bitmap.height) { x, y -> bitmap[x, y] }

    /** Crops [selection]; a [SelectionShape.Circle] gets transparent pixels outside the circle. */
    fun crop(bitmap: Bitmap, selection: Rect, shape: SelectionShape): Bitmap {
        val left = selection.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = selection.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val right = selection.right.roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = selection.bottom.roundToInt().coerceIn(top + 1, bitmap.height)
        val region = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        if (shape != SelectionShape.Circle) return region

        val circle = createBitmap(region.width, region.height)
        Canvas(circle).apply {
            clipPath(Path().apply { addOval(RectF(0f, 0f, region.width.toFloat(), region.height.toFloat()), Path.Direction.CW) })
            drawBitmap(region, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (region !== bitmap) region.recycle()
        return circle
    }
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
