package com.bo0sted.powerplusplus.module.screentext

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.unit.IntRect
import androidx.core.os.BundleCompat
import com.bo0sted.powerplusplus.capture.forEachScreenNode
import com.bo0sted.powerplusplus.textpick.TextBlock
import com.bo0sted.powerplusplus.textpick.estimateWords
import com.bo0sted.powerplusplus.textpick.normalizeTextBlocks
import com.bo0sted.powerplusplus.textpick.wordsFromCharacters
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Reads the text of every app window on screen through the accessibility node tree, with each
 * block's words for word selection.
 *
 * Every node access is an IPC into the app being read, so call this off the main thread.
 */
internal object ScreenTextReader {

    fun read(service: AccessibilityService): List<TextBlock> {
        val blocks = ArrayList<TextBlock>()
        val nodeBounds = Rect()
        service.forEachScreenNode { node, windowBounds ->
            // Offscreen children of a visible list can still hold real, current text (some chat
            // apps report message bodies that way), so only nodes without text are required to be
            // visible.
            val text = textOf(node) ?: return@forEachScreenNode
            if (!node.isVisibleToUser && node.text.isNullOrBlank()) return@forEachScreenNode
            node.getBoundsInScreen(nodeBounds)
            if (!nodeBounds.intersect(windowBounds)) return@forEachScreenNode
            val bounds = IntRect(nodeBounds.left, nodeBounds.top, nodeBounds.right, nodeBounds.bottom)
            blocks += TextBlock(text, bounds, words = wordsOf(node, text, bounds))
        }
        return normalizeTextBlocks(blocks)
    }

    /** The node's visible text, else its description; never the content of a password field. */
    private fun textOf(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        return sequenceOf(node.text, node.contentDescription, node.stateDescription)
            .map { it?.toString()?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() }
    }

    /**
     * [text]'s words, placed where the app says its characters are. Apps that don't report character
     * positions (or text that isn't the node's `text`, such as a description) get estimated places.
     */
    private fun wordsOf(node: AccessibilityNodeInfo, text: String, bounds: IntRect): List<TextBlock> {
        val raw = node.text?.toString()
        // textOf trims, so the shown text starts at the first non-space character of the node's.
        val start = raw?.indexOfFirst { !it.isWhitespace() }?.takeIf { it >= 0 && raw.startsWith(text, it) }
        val characters = start?.let { node.characterBounds(it, text.length) }
        return characters?.let { wordsFromCharacters(text, it, bounds) } ?: estimateWords(text, bounds)
    }

    /**
     * The screen bounds of [length] characters of the node's text from [start], `null` for each one
     * the app doesn't place (e.g. off screen), or `null` if the app doesn't report them at all.
     * Supported by Android's TextView, Compose and Chrome/WebView.
     */
    private fun AccessibilityNodeInfo.characterBounds(start: Int, length: Int): List<IntRect?>? {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (key !in availableExtraData) return null
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, start)
            putInt(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                length.coerceAtMost(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH),
            )
        }
        if (!refreshWithExtraData(key, arguments)) return null
        val rects = BundleCompat.getParcelableArray(extras, key, RectF::class.java) ?: return null
        return rects.map { rect ->
            (rect as RectF?)?.let { IntRect(floor(it.left).toInt(), floor(it.top).toInt(), ceil(it.right).toInt(), ceil(it.bottom).toInt()) }
        }
    }
}
