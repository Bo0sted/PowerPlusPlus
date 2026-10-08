package com.bo0sted.powerplusplus.module.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** What part of the screenshot the selection describes. All coordinates are screenshot pixels. */
enum class SelectionShape {
    Rectangle,

    /** A true circle; the crop has transparent corners. */
    Circle,

    /** Always the full image width; only its height and vertical position change. */
    HorizontalStrip,

    /** Always the full image height; only its width and horizontal position change. */
    VerticalStrip,
}

enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right }

/**
 * Selection rules. Every result stays inside [bounds], keeps the shape's invariants (circle square,
 * strips edge to edge) and is never smaller than [minSize] on an adjustable side.
 */
class SelectionGeometry(private val bounds: Size, private val minSize: Float) {

    fun default(shape: SelectionShape): Rect = when (shape) {
        SelectionShape.Rectangle -> centered(bounds.width * 0.6f, bounds.height * 0.35f)
        SelectionShape.Circle -> min(bounds.width, bounds.height).times(0.6f).let { centered(it, it) }
        SelectionShape.HorizontalStrip -> centered(bounds.width, bounds.height * 0.2f)
        SelectionShape.VerticalStrip -> centered(bounds.width * 0.4f, bounds.height)
    }

    /**
     * The largest selection of [shape]: the whole image for a rectangle or strip; for a circle, the
     * largest one that fits, centred on [current] but kept inside the image.
     */
    fun max(shape: SelectionShape, current: Rect): Rect {
        if (shape != SelectionShape.Circle) return Rect(Offset.Zero, bounds)
        val side = min(bounds.width, bounds.height)
        return placeInside(Rect(Offset(current.center.x - side / 2, current.center.y - side / 2), Size(side, side)))
    }

    fun handles(shape: SelectionShape): List<Handle> = when (shape) {
        SelectionShape.Rectangle, SelectionShape.Circle ->
            listOf(Handle.TopLeft, Handle.TopRight, Handle.BottomLeft, Handle.BottomRight)
        SelectionShape.HorizontalStrip -> listOf(Handle.Top, Handle.Bottom)
        SelectionShape.VerticalStrip -> listOf(Handle.Left, Handle.Right)
    }

    fun handlePosition(rect: Rect, handle: Handle): Offset = when (handle) {
        Handle.TopLeft -> rect.topLeft
        Handle.TopRight -> rect.topRight
        Handle.BottomLeft -> rect.bottomLeft
        Handle.BottomRight -> rect.bottomRight
        Handle.Top -> rect.topCenter
        Handle.Bottom -> rect.bottomCenter
        Handle.Left -> rect.centerLeft
        Handle.Right -> rect.centerRight
    }

    /** Moves the whole selection. Strips only move across their adjustable axis. */
    fun move(rect: Rect, delta: Offset, shape: SelectionShape): Rect {
        val allowed = when (shape) {
            SelectionShape.HorizontalStrip -> Offset(0f, delta.y)
            SelectionShape.VerticalStrip -> Offset(delta.x, 0f)
            else -> delta
        }
        return placeInside(rect.translate(allowed))
    }

    /** Drags [handle] by [delta]. The opposite side stays put. */
    fun resize(rect: Rect, handle: Handle, delta: Offset, shape: SelectionShape): Rect {
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        when (handle) {
            Handle.TopLeft -> { left += delta.x; top += delta.y }
            Handle.TopRight -> { right += delta.x; top += delta.y }
            Handle.BottomLeft -> { left += delta.x; bottom += delta.y }
            Handle.BottomRight -> { right += delta.x; bottom += delta.y }
            Handle.Top -> top += delta.y
            Handle.Bottom -> bottom += delta.y
            Handle.Left -> left += delta.x
            Handle.Right -> right += delta.x
        }
        // Keep the dragged side from crossing the fixed one and from leaving the image.
        if (handle in LEFT_SIDE) left = left.coerceIn(0f, right - minSize)
        if (handle in RIGHT_SIDE) right = right.coerceIn(left + minSize, bounds.width)
        if (handle in TOP_SIDE) top = top.coerceIn(0f, bottom - minSize)
        if (handle in BOTTOM_SIDE) bottom = bottom.coerceIn(top + minSize, bounds.height)

        if (shape != SelectionShape.Circle) return Rect(left, top, right, bottom)

        // Circle: the side follows the larger drag, anchored at the opposite corner, within the image.
        val anchorX = if (handle in LEFT_SIDE) rect.right else rect.left
        val anchorY = if (handle in TOP_SIDE) rect.bottom else rect.top
        val roomX = if (handle in LEFT_SIDE) anchorX else bounds.width - anchorX
        val roomY = if (handle in TOP_SIDE) anchorY else bounds.height - anchorY
        val side = max(right - left, bottom - top).coerceIn(minSize, min(roomX, roomY))
        val newLeft = if (handle in LEFT_SIDE) anchorX - side else anchorX
        val newTop = if (handle in TOP_SIDE) anchorY - side else anchorY
        return Rect(Offset(newLeft, newTop), Size(side, side))
    }

    private fun centered(width: Float, height: Float): Rect =
        Rect(Offset((bounds.width - width) / 2, (bounds.height - height) / 2), Size(width, height))

    /** Shifts [rect] so it lies inside the image. Assumes it isn't larger than the image. */
    private fun placeInside(rect: Rect): Rect {
        val dx = when {
            rect.left < 0f -> -rect.left
            rect.right > bounds.width -> bounds.width - rect.right
            else -> 0f
        }
        val dy = when {
            rect.top < 0f -> -rect.top
            rect.bottom > bounds.height -> bounds.height - rect.bottom
            else -> 0f
        }
        return rect.translate(dx, dy)
    }

    private companion object {
        val LEFT_SIDE = setOf(Handle.TopLeft, Handle.BottomLeft, Handle.Left)
        val RIGHT_SIDE = setOf(Handle.TopRight, Handle.BottomRight, Handle.Right)
        val TOP_SIDE = setOf(Handle.TopLeft, Handle.TopRight, Handle.Top)
        val BOTTOM_SIDE = setOf(Handle.BottomLeft, Handle.BottomRight, Handle.Bottom)
    }
}
