package com.bo0sted.powerplusplus.media

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Media found on screen by any means, in screen pixels. Pure; unit-tested. */

/** [regions] without near-duplicates (overlapping by [SAME_REGION_IOU] or more); the first is kept. */
fun distinctRegions(regions: List<IntRect>): List<IntRect> {
    val kept = ArrayList<IntRect>()
    for (r in regions) if (r.width > 0 && r.height > 0 && kept.none { overlap(it, r) >= SAME_REGION_IOU }) kept += r
    return kept
}

/** Intersection over union of two rectangles, 0..1. */
internal fun overlap(a: IntRect, b: IntRect): Float {
    val w = min(a.right, b.right) - max(a.left, b.left)
    val h = min(a.bottom, b.bottom) - max(a.top, b.top)
    if (w <= 0 || h <= 0) return 0f
    val intersection = w.toLong() * h
    val union = a.width.toLong() * a.height + b.width.toLong() * b.height - intersection
    return intersection.toFloat() / union
}

/** The order repeated taps step through: regions containing [point], smallest first, then the rest, nearest first. */
fun orderedAround(regions: List<IntRect>, point: IntOffset): List<IntRect> {
    val (around, others) = regions.partition { point.x >= it.left && point.x < it.right && point.y >= it.top && point.y < it.bottom }
    return around.sortedBy { it.width.toLong() * it.height } +
        others.sortedBy { distanceSquared(it.center, point) }
}

/** The smallest region under [point], or `null`. */
fun regionAt(regions: List<IntRect>, point: IntOffset, slopPx: Int): Int? =
    regions.indices
        .filter { i ->
            val r = regions[i]
            point.x >= r.left - slopPx && point.x < r.right + slopPx && point.y >= r.top - slopPx && point.y < r.bottom + slopPx
        }
        .minByOrNull { regions[it].width.toLong() * regions[it].height }

/**
 * Steps through media regions with repeated taps (Smart screenshot's Scan). It starts over from the
 * current selection whenever the selection isn't the region it showed last, i.e. the user moved it.
 */
class RegionCycle {
    private var order: List<IntRect> = emptyList()
    private var index = -1

    /** The region to show next for [selection], or `null` if there are none. */
    fun next(regions: List<IntRect>, selection: IntRect): IntRect? {
        if (regions.isEmpty()) return null
        if (index in order.indices && order[index].sameAs(selection)) {
            index = (index + 1) % order.size
        } else {
            order = orderedAround(regions, selection.center)
            index = 0
        }
        return order[index]
    }

    private fun IntRect.sameAs(other: IntRect) =
        abs(left - other.left) <= SAME_EDGE_PX && abs(top - other.top) <= SAME_EDGE_PX &&
            abs(right - other.right) <= SAME_EDGE_PX && abs(bottom - other.bottom) <= SAME_EDGE_PX
}

private fun distanceSquared(a: IntOffset, b: IntOffset): Long {
    val dx = (a.x - b.x).toLong()
    val dy = (a.y - b.y).toLong()
    return dx * dx + dy * dy
}

private const val SAME_REGION_IOU = 0.9f

/** Rounding between float selections and pixel regions moves an edge by at most this much. */
private const val SAME_EDGE_PX = 2
