package com.bo0sted.powerplusplus.textpick

import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Estimated word bounds for [text] shown in [bounds], for apps that don't report character positions
 * (Firefox, for one). The text is laid out with the default font at the largest size whose wrapped
 * height still fits [bounds], left-aligned and centred vertically, and each word gets its place in
 * that layout. The app's own font and spacing may differ, so the boxes can be slightly off.
 */
fun estimateWords(text: String, bounds: IntRect): List<TextBlock> {
    if (bounds.width <= 0 || bounds.height <= 0) return emptyList()
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    fun layout(size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, bounds.width).setIncludePad(false).build()
    }

    var fits = 1f
    var tooBig = bounds.height.toFloat() + 1f
    repeat(SIZE_STEPS) {
        val size = (fits + tooBig) / 2
        if (layout(size).height <= bounds.height) fits = size else tooBig = size
    }
    val layout = layout(fits)
    val top = bounds.top + (bounds.height - layout.height) / 2

    return wordRanges(text).mapNotNull { range ->
        val firstLine = layout.getLineForOffset(range.first)
        val lastLine = layout.getLineForOffset(range.last)
        val left: Float
        val right: Float
        if (firstLine == lastLine) {
            left = layout.getPrimaryHorizontal(range.first)
            right = layout.getPrimaryHorizontal(range.last) + paint.measureText(text, range.last, range.last + 1)
        } else {
            // Wrapped across lines: one box over all of them.
            left = (firstLine..lastLine).minOf { layout.getLineLeft(it) }
            right = (firstLine..lastLine).maxOf { layout.getLineRight(it) }
        }
        IntRect(
            left = bounds.left + floor(minOf(left, right)).toInt(),
            top = top + layout.getLineTop(firstLine),
            right = bounds.left + ceil(maxOf(left, right)).toInt(),
            bottom = top + layout.getLineBottom(lastLine),
        ).intersect(bounds)
            .takeIf { it.width > 0 && it.height > 0 }
            ?.let { TextBlock(text.substring(range), it) }
    }
}

/** Halvings of the size range; 12 gets within a fraction of a pixel for any screen height. */
private const val SIZE_STEPS = 12
