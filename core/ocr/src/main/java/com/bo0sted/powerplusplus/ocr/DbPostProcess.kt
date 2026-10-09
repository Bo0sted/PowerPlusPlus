package com.bo0sted.powerplusplus.ocr

import kotlin.math.min
import kotlin.math.round

/** An axis-aligned text box in image pixels, covering columns [left, right) and rows [top, bottom). */
data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Turns DBNet's probability map into text boxes, following PaddleX's `DBPostProcess` with quad boxes
 * and the "fast" score (PaddleX `text_detection/processors.py`, checked 2026-10-08):
 *
 * 1. pixels with probability > [thresh] are text;
 * 2. each text region's minimum-area rectangle is skipped if its short side is under 3 px;
 * 3. its score, the mean probability inside that rectangle, must be ≥ [boxThresh];
 * 4. it is expanded by `area × unclipRatio / perimeter` and skipped if the short side is then under 5 px;
 * 5. it is scaled to the destination size, rounded and clamped.
 *
 * Screen text is upright, so regions are treated as axis-aligned: the minimum-area rectangle is the
 * bounding box (PaddleX fits a rotated one). Regions are 8-connected like OpenCV's contours; holes
 * inside a region are ignored (PaddleX's `RETR_LIST` would also score each hole as a box).
 */
internal object DbPostProcess {

    fun boxes(
        map: FloatArray,
        width: Int,
        height: Int,
        destWidth: Int,
        destHeight: Int,
        thresh: Float,
        boxThresh: Float,
        unclipRatio: Float,
        maxCandidates: Int,
    ): List<TextBox> {
        require(map.size == width * height) { "Map is ${map.size}, expected ${width}×$height" }
        val scaleX = destWidth.toDouble() / width
        val scaleY = destHeight.toDouble() / height
        val boxes = ArrayList<TextBox>()
        regions(map, width, height, thresh, maxCandidates) { minX, minY, maxX, maxY ->
            // Rectangle corners are pixel centres, as in OpenCV: a one-pixel-wide region has width 0.
            val w = maxX - minX
            val h = maxY - minY
            if (min(w, h) < MIN_SIZE) return@regions
            if (meanInside(map, width, minX, minY, maxX, maxY) < boxThresh) return@regions

            // Clipper offsets each edge by the distance and rounds the vertices.
            val distance = w.toDouble() * h * unclipRatio / (2.0 * (w + h))
            val left = roundHalfAwayFromZero(minX - distance)
            val top = roundHalfAwayFromZero(minY - distance)
            val right = roundHalfAwayFromZero(maxX + distance)
            val bottom = roundHalfAwayFromZero(maxY + distance)
            if (min(right - left, bottom - top) < MIN_SIZE + 2) return@regions

            boxes += TextBox(
                left = scale(left, scaleX, destWidth),
                top = scale(top, scaleY, destHeight),
                right = scale(right, scaleX, destWidth),
                bottom = scale(bottom, scaleY, destHeight),
            )
        }
        return boxes
    }

    /** Calls [onRegion] with the bounding box of each 8-connected region of pixels above [thresh]. */
    private inline fun regions(
        map: FloatArray,
        width: Int,
        height: Int,
        thresh: Float,
        maxCandidates: Int,
        onRegion: (minX: Int, minY: Int, maxX: Int, maxY: Int) -> Unit,
    ) {
        val visited = BooleanArray(map.size)
        val stack = IntArray(map.size)
        var found = 0
        for (start in map.indices) {
            if (visited[start] || map[start] <= thresh) continue
            if (found++ == maxCandidates) return
            var minX = start % width
            var maxX = minX
            var minY = start / width
            var maxY = minY
            var size = 0
            stack[size++] = start
            visited[start] = true
            while (size > 0) {
                val index = stack[--size]
                val x = index % width
                val y = index / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (ny in maxOf(y - 1, 0)..minOf(y + 1, height - 1)) {
                    for (nx in maxOf(x - 1, 0)..minOf(x + 1, width - 1)) {
                        val neighbour = ny * width + nx
                        if (!visited[neighbour] && map[neighbour] > thresh) {
                            visited[neighbour] = true
                            stack[size++] = neighbour
                        }
                    }
                }
            }
            onRegion(minX, minY, maxX, maxY)
        }
    }

    /** PaddleX's `box_score_fast`: the mean over the rectangle, edges included. */
    private fun meanInside(map: FloatArray, width: Int, minX: Int, minY: Int, maxX: Int, maxY: Int): Float {
        var sum = 0.0
        for (y in minY..maxY) for (x in minX..maxX) sum += map[y * width + x]
        return (sum / ((maxX - minX + 1) * (maxY - minY + 1))).toFloat()
    }

    /** Python's `round` (half to even), then clamped to the destination, as PaddleX does. */
    private fun scale(value: Int, factor: Double, limit: Int): Int = round(value * factor).toInt().coerceIn(0, limit)

    private fun roundHalfAwayFromZero(value: Double): Int = if (value < 0) (value - 0.5).toInt() else (value + 0.5).toInt()

    private const val MIN_SIZE = 3
}
