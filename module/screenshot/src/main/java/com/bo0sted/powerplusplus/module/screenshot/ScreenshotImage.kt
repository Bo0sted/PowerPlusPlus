package com.bo0sted.powerplusplus.module.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.core.graphics.createBitmap
import com.bo0sted.powerplusplus.selection.SelectionShape
import kotlin.math.roundToInt

/** Cropping for screenshots. Can be slow; call it off the main thread. */
internal object ScreenshotImage {

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
