package com.bo0sted.powerplusplus.module.screentext

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.unit.IntRect
import com.bo0sted.powerplusplus.capture.forEachScreenNode
import com.bo0sted.powerplusplus.textpick.TextBlock
import com.bo0sted.powerplusplus.textpick.normalizeTextBlocks

/**
 * Reads the text of every app window on screen through the accessibility node tree.
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
            blocks += TextBlock(text, IntRect(nodeBounds.left, nodeBounds.top, nodeBounds.right, nodeBounds.bottom))
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
}
