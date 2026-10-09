package com.bo0sted.powerplusplus.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Crop169
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Selection rules for [image], with the 48 dp minimum size every selection screen uses. */
@Composable
fun rememberSelectionGeometry(image: ImageBitmap): SelectionGeometry {
    val density = LocalDensity.current
    // Keyed on the size, not the image, so a retake keeps the current selection.
    return remember(image.width, image.height, density) {
        SelectionGeometry(Size(image.width.toFloat(), image.height.toFloat()), minSize = with(density) { 48.dp.toPx() })
    }
}

/**
 * The frozen [image] drawn full-screen with [selection] cut out of a dark scrim, framed, with
 * handles and a centre crosshair. Drag inside to move it, drag a handle to resize it (32 dp slop);
 * a tap anywhere else calls [onTapOutside]. Without [interactive], it only shows the selection.
 *
 * @param scale screen pixels per image pixel.
 * @param onDraggingChange true while the user drags the selection or a handle.
 */
@Composable
fun SelectionCanvas(
    image: ImageBitmap,
    geometry: SelectionGeometry,
    shape: SelectionShape,
    selection: Rect,
    scale: Float,
    onSelectionChange: (Rect) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onTapOutside: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.surface
    // The gesture loop outlives recompositions; it must see the latest values.
    val currentShape by rememberUpdatedState(shape)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val currentOnTapOutside by rememberUpdatedState(onTapOutside)
    val onScreen = Rect(selection.topLeft * scale, selection.size * scale)

    Canvas(
        modifier.pointerInput(geometry, scale, interactive) {
            if (!interactive) return@pointerInput
            val handleSlop = 32.dp.toPx()
            val tapSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position / scale
                val drag: Drag? = geometry.handles(currentShape)
                    .firstOrNull { (geometry.handlePosition(currentSelection, it) * scale - down.position).getDistance() <= handleSlop }
                    ?.let { Drag.Resize(it) }
                    ?: Drag.Move.takeIf { contains(currentSelection, currentShape, start) }
                var travelled = 0f
                var updated = currentSelection
                do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.positionChange()
                    travelled += delta.getDistance()
                    if (drag != null && delta != Offset.Zero) {
                        currentOnDraggingChange(true)
                        val imageDelta = delta / scale
                        updated = when (drag) {
                            Drag.Move -> geometry.move(updated, imageDelta, currentShape)
                            is Drag.Resize -> geometry.resize(updated, drag.handle, imageDelta, currentShape)
                        }
                        currentOnSelectionChange(updated)
                    }
                    change.consume()
                } while (change.pressed)
                currentOnDraggingChange(false)
                if (drag == null && travelled < tapSlop) currentOnTapOutside()
            }
        },
    ) {
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val selected = Path().apply { if (shape == SelectionShape.Circle) addOval(onScreen) else addRect(onScreen) }
        val outside = Path.combine(PathOperation.Difference, Path().apply { addRect(Rect(Offset.Zero, size)) }, selected)
        drawPath(outside, Color.Black.copy(alpha = 0.58f))
        drawPath(selected, accent, style = Stroke(width = 3.dp.toPx()))
        if (!interactive) return@Canvas

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
}

/** A toolbar toggle for [value]; checked when it is [current]. */
@Composable
fun ShapeToggle(value: SelectionShape, current: SelectionShape, onSelect: (SelectionShape) -> Unit) {
    val (icon, label) = when (value) {
        SelectionShape.Rectangle -> Icons.Rounded.CropSquare to R.string.selection_shape_rectangle
        SelectionShape.Circle -> Icons.Outlined.Circle to R.string.selection_shape_circle
        SelectionShape.HorizontalStrip -> Icons.Rounded.Crop169 to R.string.selection_shape_horizontal
        SelectionShape.VerticalStrip -> Icons.Rounded.CropPortrait to R.string.selection_shape_vertical
    }
    IconToggleButton(checked = value == current, onCheckedChange = { onSelect(value) }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

private sealed interface Drag {
    data object Move : Drag
    data class Resize(val handle: Handle) : Drag
}

private fun contains(selection: Rect, shape: SelectionShape, point: Offset): Boolean {
    if (!selection.contains(point)) return false
    if (shape != SelectionShape.Circle) return true
    return (point - selection.center).getDistance() <= selection.width / 2
}
