package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * A zoomed view of content the size of the screen: a point `c` of the content is shown at
 * `c × zoom + pan`. The content always covers the whole view, so it can't be panned past its edges.
 */
data class ZoomViewport(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {

    /** The content point under [point] on screen. */
    fun toContent(point: Offset): Offset = (point - pan) / zoom

    /**
     * After a pinch around [centroid] that scaled by [zoomChange] and moved by [panChange], in a
     * view of [size]: the point under the fingers stays under them, within [MAX_ZOOM].
     */
    fun transformed(centroid: Offset, zoomChange: Float, panChange: Offset, size: Size): ZoomViewport {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val anchor = toContent(centroid)
        val newPan = centroid - anchor * newZoom + panChange
        return ZoomViewport(newZoom, clampPan(newPan, newZoom, size))
    }

    private fun clampPan(pan: Offset, zoom: Float, size: Size) = Offset(
        pan.x.coerceIn(size.width - size.width * zoom, 0f),
        pan.y.coerceIn(size.height - size.height * zoom, 0f),
    )

    companion object {
        const val MAX_ZOOM = 6f
    }
}
