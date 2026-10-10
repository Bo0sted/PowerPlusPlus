package com.bo0sted.powerplusplus.media

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import androidx.compose.ui.unit.IntRect
import com.bo0sted.powerplusplus.capture.forEachScreenNode

/**
 * Finds pictures and videos through the accessibility node tree: the exact bounds apps report.
 *
 * Class names (verified 2026-10-09 in AOSP `main`, androidx-main, Chromium and Firefox sources):
 * - `android.widget.ImageView`: `ImageView`, and Compose `Image` (Role.Image), which sets the role
 *   only when the image has a content description, so decorative Compose images aren't reported.
 * - `android.widget.Image`: images in Chrome/WebView and in Firefox (GeckoView roles GRAPHIC, CANVAS…);
 *   Chrome reports clickable ones as `android.widget.ImageButton`.
 * - `android.widget.VideoView`.
 * `SurfaceView` and `TextureView`, where most players draw, don't override
 * `getAccessibilityClassName`, so they report `android.view.View`: players are recognised by view id
 * instead (Media3/ExoPlayer's `exo_content_frame`, or an id naming a video or player), which needs
 * the service's `flagReportViewIds`.
 *
 * The tree is the live screen, which can differ from an earlier capture (e.g. apps that hide
 * themselves when they lose focus). Every node access is an IPC; call off the main thread.
 */
object ScreenMediaFinder {

    fun find(service: AccessibilityService, minSidePx: Int): List<IntRect> {
        val found = ArrayList<IntRect>()
        val bounds = Rect()
        service.forEachScreenNode { node, windowBounds ->
            if (!node.isVisibleToUser) return@forEachScreenNode
            val className = node.className?.toString()
            val viewId = node.viewIdResourceName?.substringAfterLast('/')
            if (className !in MEDIA_CLASSES && (viewId == null || !PLAYER_ID.containsMatchIn(viewId))) return@forEachScreenNode
            node.getBoundsInScreen(bounds)
            if (!bounds.intersect(windowBounds)) return@forEachScreenNode
            if (bounds.width() < minSidePx || bounds.height() < minSidePx) return@forEachScreenNode
            found += IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        return distinctRegions(found.sortedByDescending { it.width.toLong() * it.height })
    }

    private val MEDIA_CLASSES = setOf(
        "android.widget.ImageView",
        "android.widget.Image",
        "android.widget.ImageButton",
        "android.widget.VideoView",
    )

    private val PLAYER_ID = Regex("exo_content_frame|video|player", RegexOption.IGNORE_CASE)
}
