package com.bo0sted.powerplusplus.module.screentext

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.unit.IntRect
import com.bo0sted.powerplusplus.textpick.TextBlock
import com.bo0sted.powerplusplus.textpick.normalizeTextBlocks

/**
 * Reads the text of every window on screen through the accessibility node tree.
 *
 * Every node access is an IPC into the app being read, so call this off the main thread.
 */
internal object ScreenTextReader {

    fun read(service: AccessibilityService): List<TextBlock> {
        val ownPackage = service.packageName
        val blocks = ArrayList<TextBlock>()
        val windowBounds = Rect()
        val nodeBounds = Rect()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            window.getBoundsInScreen(windowBounds)

            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.add(root)
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                for (i in 0 until node.childCount) node.getChild(i)?.let(pending::add)

                // Offscreen children of a visible list can still hold real, current text (some
                // chat apps report message bodies that way), so only nodes without text are
                // required to be visible.
                val text = textOf(node) ?: continue
                if (!node.isVisibleToUser && node.text.isNullOrBlank()) continue
                node.getBoundsInScreen(nodeBounds)
                if (!nodeBounds.intersect(windowBounds)) continue
                blocks += TextBlock(text, IntRect(nodeBounds.left, nodeBounds.top, nodeBounds.right, nodeBounds.bottom))
            }
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
