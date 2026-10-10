package com.bo0sted.powerplusplus.media

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntRect
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds pictures and videos in a screen capture from its pixels alone ("Scan"). No model and no
 * download: on-screen media are upright rectangles on app backgrounds, which is a geometry problem.
 *
 * 1. The capture is shrunk to about [WORK_WIDTH] wide.
 * 2. **Background** is every large flat area (neighbouring pixels within [FLAT_TOLERANCE], and within
 *    [SEED_TOLERANCE] of where the area starts) that touches the edge of the area being split.
 * 3. Every other 8-connected component at least [minSidePx] each way is a block; blocks with many
 *    colours, or no colour covering most of them, are media (buttons and plain cards are neither).
 * 4. A block whose own edge is mostly flat (a card with padding, a letterboxed video) is split again
 *    the same way, so the picture inside is found too. Both are kept: tapping or cycling chooses.
 * 5. Each edge is snapped to the strongest contrast within a few full-resolution pixels.
 *
 * Known limit: a picture whose edge has the same colour as the app around it (a white meme on a
 * white app) has no edge to find. Slow (tens to hundreds of ms); call off the main thread.
 */
object MediaScan {

    /** Media rectangles in [image] pixels, largest first. [image] must be a software bitmap. */
    fun scan(image: Bitmap, minSidePx: Int): List<IntRect> {
        val factor = max(1, (image.width / WORK_WIDTH.toFloat()).roundToInt())
        val small = if (factor == 1) image else image.scale(image.width / factor, image.height / factor)
        val smallPixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val found = scanPixels(smallPixels, small.width, small.height, max(1, minSidePx / factor))
        if (small !== image) small.recycle()
        if (found.isEmpty()) return emptyList()

        val full = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
        val pixel = { x: Int, y: Int -> full[y * image.width + x] }
        return found
            .map { snapEdges(scaleUp(it, factor, image.width, image.height), image.width, image.height, factor * 2, pixel) }
            .let(::distinctRegions)
            .sortedByDescending { it.width.toLong() * it.height }
    }

    /** The media in a [width]×[height] ARGB image, in its pixels, before edge snapping. */
    internal fun scanPixels(pixels: IntArray, width: Int, height: Int, minSide: Int): List<IntRect> {
        val found = ArrayList<IntRect>()
        split(pixels, width, IntRect(0, 0, width, height), minSide, depth = 0, found)
        return distinctRegions(found)
    }

    private fun split(pixels: IntArray, stride: Int, area: IntRect, minSide: Int, depth: Int, found: MutableList<IntRect>) {
        val w = area.width
        val h = area.height
        val background = background(pixels, stride, area, minSide)
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (background[start] || seen[start]) continue
            // 8-connected foreground component: its bounding box.
            var minX = start % w
            var maxX = minX
            var minY = start / w
            var maxY = minY
            var size = 0
            stack[size++] = start
            seen[start] = true
            while (size > 0) {
                val i = stack[--size]
                val x = i % w
                val y = i / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (ny in maxOf(y - 1, 0)..minOf(y + 1, h - 1)) {
                    for (nx in maxOf(x - 1, 0)..minOf(x + 1, w - 1)) {
                        val n = ny * w + nx
                        if (!background[n] && !seen[n]) {
                            seen[n] = true
                            stack[size++] = n
                        }
                    }
                }
            }
            if (maxX - minX + 1 < minSide || maxY - minY + 1 < minSide) continue
            val block = IntRect(area.left + minX, area.top + minY, area.left + maxX + 1, area.top + maxY + 1)
            if (looksLikeMedia(pixels, stride, block)) found += block
            // A block that is the whole area found no background: splitting it again changes nothing.
            if (depth < MAX_DEPTH && block != area && mostlyFlatEdge(pixels, stride, block, minSide)) {
                split(pixels, stride, block, minSide, depth + 1, found)
            }
        }
    }

    /**
     * Marks the large flat regions that touch [area]'s edge. Flat means each pixel is within
     * [FLAT_TOLERANCE] of the neighbour it was reached from and within [SEED_TOLERANCE] of the
     * region's first pixel, so a gradient inside a photo can't creep far.
     */
    private fun background(pixels: IntArray, stride: Int, area: IntRect, minSide: Int): BooleanArray {
        val w = area.width
        val h = area.height
        val marked = BooleanArray(w * h)
        val tried = BooleanArray(w * h)
        val queue = IntArray(w * h)
        // Small enough that a 1 px divider across the screen counts (it would otherwise join
        // whatever touches it); large enough that a lone flat speck at a photo's edge doesn't.
        val minRegion = minSide
        fun at(i: Int) = pixels[(area.top + i / w) * stride + area.left + i % w]
        fun fill(start: Int) {
            if (tried[start]) return
            val seed = at(start)
            var head = 0
            var tail = 0
            queue[tail++] = start
            tried[start] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                val color = at(i)
                for (k in 0 until 4) {
                    val n = when (k) {
                        0 -> if (x > 0) i - 1 else continue
                        1 -> if (x < w - 1) i + 1 else continue
                        2 -> if (y > 0) i - w else continue
                        else -> if (y < h - 1) i + w else continue
                    }
                    if (tried[n]) continue
                    val c = at(n)
                    if (colorDistance(c, color) <= FLAT_TOLERANCE && colorDistance(c, seed) <= SEED_TOLERANCE) {
                        tried[n] = true
                        queue[tail++] = n
                    }
                }
            }
            // The queue holds exactly this region's pixels.
            if (tail >= minRegion) for (k in 0 until tail) marked[queue[k]] = true
        }
        for (x in 0 until w) {
            fill(x)
            fill((h - 1) * w + x)
        }
        for (y in 0 until h) {
            fill(y * w)
            fill(y * w + w - 1)
        }
        return marked
    }

    /** Many colours, or none covering most of it: photos, memes, video frames; not buttons or plain cards. */
    private fun looksLikeMedia(pixels: IntArray, stride: Int, block: IntRect): Boolean {
        val counts = IntArray(4096)
        var distinct = 0
        var total = 0
        for (y in block.top until block.bottom) {
            for (x in block.left until block.right) {
                val c = pixels[y * stride + x]
                val bucket = (c shr 12 and 0xF00) or (c shr 8 and 0xF0) or (c shr 4 and 0xF)
                if (counts[bucket]++ == 0) distinct++
                total++
            }
        }
        if (distinct >= MIN_COLOURS) return true
        return counts.max() <= total * MAX_DOMINANT_SHARE
    }

    /** Whether most of [block]'s outline is flat, as around a card's padding or a letterboxed video. */
    private fun mostlyFlatEdge(pixels: IntArray, stride: Int, block: IntRect, minSide: Int): Boolean {
        val inner = background(pixels, stride, block, minSide)
        val w = block.width
        val h = block.height
        var flat = 0
        var total = 0
        for (x in 0 until w) {
            if (inner[x]) flat++
            if (inner[(h - 1) * w + x]) flat++
            total += 2
        }
        for (y in 1 until h - 1) {
            if (inner[y * w]) flat++
            if (inner[y * w + w - 1]) flat++
            total += 2
        }
        return flat >= total * FLAT_EDGE_SHARE
    }

    private fun scaleUp(r: IntRect, factor: Int, width: Int, height: Int) = IntRect(
        (r.left * factor).coerceIn(0, width - 1),
        (r.top * factor).coerceIn(0, height - 1),
        (r.right * factor).coerceIn(1, width),
        (r.bottom * factor).coerceIn(1, height),
    )

    /**
     * Moves each edge of [r] by up to [reach] pixels to where neighbouring rows (or columns) differ
     * most along it, which undoes the rounding of working at a smaller size.
     */
    internal fun snapEdges(r: IntRect, width: Int, height: Int, reach: Int, pixel: (Int, Int) -> Int): IntRect {
        fun rowContrast(y: Int): Long { // Between rows y - 1 and y.
            if (y <= 0 || y >= height) return -1
            var sum = 0L
            for (x in r.left until r.right) sum += colorDistance(pixel(x, y - 1), pixel(x, y))
            return sum
        }
        fun columnContrast(x: Int): Long { // Between columns x - 1 and x.
            if (x <= 0 || x >= width) return -1
            var sum = 0L
            for (y in r.top until r.bottom) sum += colorDistance(pixel(x - 1, y), pixel(x, y))
            return sum
        }
        fun best(around: Int, contrast: (Int) -> Long): Int =
            (around - reach..around + reach).maxByOrNull { contrast(it) }?.takeIf { contrast(it) > 0 } ?: around
        val top = best(r.top, ::rowContrast)
        val bottom = best(r.bottom, ::rowContrast)
        val left = best(r.left, ::columnContrast)
        val right = best(r.right, ::columnContrast)
        // Never let snapping collapse or invert the rectangle.
        if (bottom - top < r.height / 2 || right - left < r.width / 2) return r
        return IntRect(left.coerceIn(0, width), top.coerceIn(0, height), right.coerceIn(0, width), bottom.coerceIn(0, height))
    }

    /** The largest difference between the red, green and blue channels of two ARGB colours. */
    internal fun colorDistance(a: Int, b: Int): Int = max(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        max(abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
    )

    /** Width the capture is shrunk to before scanning; 1344 px screens are scanned at a quarter. */
    private const val WORK_WIDTH = 360
    private const val FLAT_TOLERANCE = 6
    private const val SEED_TOLERANCE = 12
    private const val MIN_COLOURS = 24
    private const val MAX_DOMINANT_SHARE = 0.5f
    private const val FLAT_EDGE_SHARE = 0.5f
    private const val MAX_DEPTH = 3
}
