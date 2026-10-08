package com.bo0sted.powerplusplus.module.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop169
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntSize.Companion.Zero
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

enum class ScreenshotAction { Copy, Save, Share, Edit }

private sealed interface Drag {
    data object Move : Drag
    data class Resize(val handle: Handle) : Drag
}

/**
 * Step 1 of Smart screenshot: the frozen screenshot with a selection to move and resize. The shape
 * toolbar floats above the selection, the action toolbar below it; both hide while dragging.
 * Tapping outside the selection cancels. Under the shape toolbar: Max, Default and — when
 * [onRetake] is given — Retake, which replaces the frozen capture with a fresh one.
 *
 * @param onAction receives the selection in screenshot pixels.
 */
@Composable
internal fun SelectScreen(
    image: ImageBitmap,
    onAction: (ScreenshotAction, Rect, SelectionShape) -> Unit,
    onCancel: () -> Unit,
    onRetake: (() -> Unit)?,
    retaking: Boolean,
) {
    val density = LocalDensity.current
    // Keyed on the size, not the image, so a retake keeps the current selection.
    val geometry = remember(image.width, image.height) {
        SelectionGeometry(Size(image.width.toFloat(), image.height.toFloat()), minSize = with(density) { 48.dp.toPx() })
    }
    var shape by remember { mutableStateOf(SelectionShape.Rectangle) }
    var selection by remember { mutableStateOf(geometry.default(SelectionShape.Rectangle)) }
    var dragging by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.surface

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        // The screenshot covers the whole display, as does this window; scale only guards rounding.
        val scale = viewSize.width / image.width
        val onScreen = Rect(selection.topLeft * scale, selection.size * scale)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geometry, scale) {
                    val handleSlop = 32.dp.toPx()
                    val tapSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position / scale
                        val drag: Drag? = geometry.handles(shape)
                            .firstOrNull { (geometry.handlePosition(selection, it) * scale - down.position).getDistance() <= handleSlop }
                            ?.let { Drag.Resize(it) }
                            ?: Drag.Move.takeIf { contains(selection, shape, start) }
                        var travelled = 0f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            travelled += delta.getDistance()
                            if (drag != null && delta != Offset.Zero) {
                                dragging = true
                                val imageDelta = delta / scale
                                selection = when (drag) {
                                    Drag.Move -> geometry.move(selection, imageDelta, shape)
                                    is Drag.Resize -> geometry.resize(selection, drag.handle, imageDelta, shape)
                                }
                            }
                            change.consume()
                        } while (change.pressed)
                        dragging = false
                        if (drag == null && travelled < tapSlop) onCancel()
                    }
                },
        ) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val selected = Path().apply { if (shape == SelectionShape.Circle) addOval(onScreen) else addRect(onScreen) }
            val outside = Path.combine(PathOperation.Difference, Path().apply { addRect(Rect(Offset.Zero, size)) }, selected)
            drawPath(outside, Color.Black.copy(alpha = 0.58f))
            drawPath(selected, accent, style = Stroke(width = 3.dp.toPx()))

            val handleRadius = 8.dp.toPx()
            geometry.handles(shape).forEach { handle ->
                val center = geometry.handlePosition(selection, handle) * scale
                drawCircle(accent, handleRadius, center)
                drawCircle(handleColor, handleRadius, center, style = Stroke(width = 2.dp.toPx()))
            }
            val arm = 14.dp.toPx()
            val c = onScreen.center
            drawLine(accent, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 2.dp.toPx(), StrokeCap.Round)
        }

        if (!dragging) {
            val safe = WindowInsets.safeDrawing
            val layoutDirection = LocalLayoutDirection.current
            val margin = with(density) { 12.dp.toPx() }
            val area = Rect(
                safe.getLeft(density, layoutDirection) + margin,
                safe.getTop(density) + margin,
                viewSize.width - safe.getRight(density, layoutDirection) - margin,
                viewSize.height - safe.getBottom(density) - margin,
            )
            FloatingToolbar(anchor = onScreen, area = area, above = true) {
                ShapeButton(SelectionShape.Rectangle, Icons.Rounded.CropSquare, R.string.screenshot_shape_rectangle, shape) { shape = it; selection = geometry.default(it) }
                ShapeButton(SelectionShape.Circle, Icons.Outlined.Circle, R.string.screenshot_shape_circle, shape) { shape = it; selection = geometry.default(it) }
                ShapeButton(SelectionShape.HorizontalStrip, Icons.Rounded.Crop169, R.string.screenshot_shape_horizontal, shape) { shape = it; selection = geometry.default(it) }
                ShapeButton(SelectionShape.VerticalStrip, Icons.Rounded.CropPortrait, R.string.screenshot_shape_vertical, shape) { shape = it; selection = geometry.default(it) }
            }
            // Directly under the shape toolbar.
            FloatingToolbar(anchor = onScreen, area = area, above = true, stackIndex = 1) {
                TextButton(onClick = { selection = geometry.max(shape, selection) }) { Text(stringResource(R.string.screenshot_size_max)) }
                TextButton(onClick = { selection = geometry.default(shape) }) { Text(stringResource(R.string.screenshot_size_default)) }
                if (onRetake != null) {
                    TextButton(onClick = onRetake, enabled = !retaking) { Text(stringResource(R.string.screenshot_retake)) }
                }
            }
            FloatingToolbar(anchor = onScreen, area = area, above = false) {
                ActionButton(Icons.Rounded.ContentCopy, R.string.screenshot_copy) { onAction(ScreenshotAction.Copy, selection, shape) }
                ActionButton(Icons.Rounded.Save, R.string.screenshot_save) { onAction(ScreenshotAction.Save, selection, shape) }
                ActionButton(Icons.Rounded.Share, R.string.screenshot_share) { onAction(ScreenshotAction.Share, selection, shape) }
                ActionButton(Icons.Rounded.Edit, R.string.screenshot_edit) { onAction(ScreenshotAction.Edit, selection, shape) }
            }
        }
    }
}

private fun contains(selection: Rect, shape: SelectionShape, point: Offset): Boolean {
    if (!selection.contains(point)) return false
    if (shape != SelectionShape.Circle) return true
    return (point - selection.center).getDistance() <= selection.width / 2
}

/**
 * A toolbar centered on [anchor], just above or below it, kept inside [area]. Above the anchor,
 * [stackIndex] 0 is the top toolbar and 1 sits directly under it (both still above the anchor).
 */
@Composable
private fun FloatingToolbar(anchor: Rect, area: Rect, above: Boolean, stackIndex: Int = 0, content: @Composable () -> Unit) {
    var size by remember { mutableStateOf(Zero) }
    val gap = with(LocalDensity.current) { 14.dp.toPx() }
    val rowStep = with(LocalDensity.current) { (TOOLBAR_HEIGHT + 8.dp).toPx() }
    val measured = size.toSize()
    val x = (anchor.center.x - measured.width / 2).coerceIn(area.left, (area.right - measured.width).coerceAtLeast(area.left))
    // Rows above the anchor stack upward: the last row (highest index) is closest to the anchor.
    val rowsAbove = if (above) (ABOVE_ROWS - 1 - stackIndex) else 0
    val preferredY = if (above) anchor.top - measured.height - gap - rowsAbove * rowStep else anchor.bottom + gap
    val minY = if (above) area.top + stackIndex * rowStep else area.top
    val y = preferredY.coerceIn(minY, (area.bottom - measured.height).coerceAtLeast(minY))
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { size = it },
    ) {
        Row(Modifier.padding(horizontal = 6.dp)) { content() }
    }
}

@Composable
private fun ShapeButton(
    value: SelectionShape,
    icon: ImageVector,
    label: Int,
    current: SelectionShape,
    onSelect: (SelectionShape) -> Unit,
) {
    IconToggleButton(checked = value == current, onCheckedChange = { onSelect(value) }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}

private val TOOLBAR_HEIGHT = 48.dp
private const val ABOVE_ROWS = 2
