package com.bo0sted.powerplusplus.module.screenshot.editor

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Blur and pixelate for the editor. Strength is 0..1 and maps exponentially, so the slider gives as
 * much travel to subtle settings as to heavy ones. All functions are slow; call them off the main thread.
 */
object EditorEffects {

    /** Blur radius in image pixels: ~1.5 px (barely visible) to 300 px (unrecognisable). */
    fun blurRadius(strength: Float): Float = 1.5f * 200f.pow(strength.coerceIn(0f, 1f))

    /** Pixel block size in image pixels: 2 px (fine) to 200 px (huge blocks). */
    fun pixelBlock(strength: Float): Int = (2f * 100f.pow(strength.coerceIn(0f, 1f))).roundToInt()

    /** Quantises strength so nearby slider values share one processed image. */
    fun cacheKey(kind: EffectKind, strength: Float): Pair<EffectKind, Int> = kind to (strength.coerceIn(0f, 1f) * 100).roundToInt()

    /** The processed image. For a tiny [source] this may be [source] itself; never recycle both. */
    fun apply(source: Bitmap, kind: EffectKind, strength: Float): Bitmap = when (kind) {
        EffectKind.Blur -> blur(source, blurRadius(strength))
        EffectKind.Pixelate -> pixelate(source, pixelBlock(strength))
    }

    /**
     * Approximates a Gaussian blur by shrinking and re-enlarging with bilinear filtering. Shrinking
     * happens in halves: one big bilinear step would sample only a few source pixels and alias.
     */
    private fun blur(source: Bitmap, radius: Float): Bitmap {
        val divisor = max(1.5f, radius / 2f)
        val targetWidth = max(1, (source.width / divisor).roundToInt())
        val targetHeight = max(1, (source.height / divisor).roundToInt())

        var current = source
        while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
            current = current.scaledTo(current.width / 2, current.height / 2, filter = true, recycleInput = current !== source)
        }
        current = current.scaledTo(targetWidth, targetHeight, filter = true, recycleInput = current !== source)
        while (current.width * 2 <= source.width && current.height * 2 <= source.height) {
            current = current.scaledTo(current.width * 2, current.height * 2, filter = true, recycleInput = current !== source)
        }
        return current.scaledTo(source.width, source.height, filter = true, recycleInput = current !== source)
    }

    /** Averages each block (bilinear shrink) then enlarges without filtering, giving hard blocks. */
    private fun pixelate(source: Bitmap, block: Int): Bitmap {
        val small = source.scaledTo(
            max(1, ceil(source.width / block.toFloat()).toInt()),
            max(1, ceil(source.height / block.toFloat()).toInt()),
            filter = true,
            recycleInput = false,
        )
        return small.scaledTo(source.width, source.height, filter = false, recycleInput = small !== source)
    }

    private fun Bitmap.scaledTo(width: Int, height: Int, filter: Boolean, recycleInput: Boolean): Bitmap {
        if (width == this.width && height == this.height) return this
        val scaled = scale(width, height, filter)
        if (recycleInput && scaled !== this) recycle()
        return scaled
    }
}
