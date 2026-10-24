package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.unit.IntRect

/**
 * A piece of on-screen text and where it is, in screen pixels.
 *
 * @property words its words, each a block of its own, for word selection ([wordRanges]); `null`
 *   where the source can't place words (OCR), which then offers block selection only.
 */
data class TextBlock(val text: String, val bounds: IntRect, val words: List<TextBlock>? = null)

/**
 * Cleans up raw blocks for display: drops empty or blank ones, drops any block whose bounds strictly
 * contain a smaller block (a container summarising its children's text), removes exact duplicates,
 * and sorts in reading order (top to bottom, then left to right).
 */
fun normalizeTextBlocks(raw: List<TextBlock>): List<TextBlock> {
    val usable = raw.filter { it.text.isNotBlank() && it.bounds.width > 0 && it.bounds.height > 0 }
    val leaves = usable.filter { candidate ->
        usable.none { other -> other !== candidate && candidate.bounds.encloses(other.bounds) && other.bounds.area < candidate.bounds.area }
    }
    return leaves.distinct().sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
}

/**
 * The block under ([x], [y]), with [slopPx] of tolerance around each block. When blocks overlap,
 * the smallest wins, so nested or adjacent text stays selectable.
 */
fun hitTest(blocks: List<TextBlock>, x: Int, y: Int, slopPx: Int): Int? =
    blocks.indices
        .filter { i ->
            val b = blocks[i].bounds
            x >= b.left - slopPx && x <= b.right + slopPx && y >= b.top - slopPx && y <= b.bottom + slopPx
        }
        .minByOrNull { blocks[it].bounds.area }

private val IntRect.area: Long get() = width.toLong() * height.toLong()

private fun IntRect.encloses(other: IntRect): Boolean =
    left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
