package com.bo0sted.powerplusplus.module.ocrtext

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/** This selection in whole pixels, kept inside a [width]×[height] image and at least 1 px each way. */
internal fun Rect.toRegion(width: Int, height: Int): IntRect {
    val left = left.roundToInt().coerceIn(0, width - 1)
    val top = top.roundToInt().coerceIn(0, height - 1)
    return IntRect(
        left = left,
        top = top,
        right = right.roundToInt().coerceIn(left + 1, width),
        bottom = bottom.roundToInt().coerceIn(top + 1, height),
    )
}
