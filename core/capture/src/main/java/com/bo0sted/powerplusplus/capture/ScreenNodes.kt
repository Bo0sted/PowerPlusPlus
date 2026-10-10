package com.bo0sted.powerplusplus.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Visits every node of every app window on screen, breadth first, with its window's bounds in screen
 * pixels. Accessibility overlays (ours included), the keyboard and Power++'s own windows are skipped.
 *
 * Every node access is an IPC into the app being read, so call this off the main thread.
 */
fun AccessibilityService.forEachScreenNode(visit: (node: AccessibilityNodeInfo, windowBounds: Rect) -> Unit) {
    val ownPackage = packageName
    val windowBounds = Rect()
    for (window in windows) {
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
            visit(node, windowBounds)
        }
    }
}
