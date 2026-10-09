package com.bo0sted.powerplusplus.module.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize.Companion.Zero
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.layout.offset
import com.bo0sted.powerplusplus.selection.SelectionCanvas
import com.bo0sted.powerplusplus.selection.SelectionShape
import com.bo0sted.powerplusplus.selection.ShapeToggle
import com.bo0sted.powerplusplus.selection.rememberSelectionGeometry
import kotlin.math.roundToInt
import com.bo0sted.powerplusplus.selection.R as SelectionR

enum class ScreenshotAction { Copy, Save, Share, Edit }

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
    val geometry = rememberSelectionGeometry(image)
    var shape by remember { mutableStateOf(SelectionShape.Rectangle) }
    var selection by remember { mutableStateOf(geometry.default(SelectionShape.Rectangle)) }
    var dragging by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        // The screenshot covers the whole display, as does this window; scale only guards rounding.
        val scale = viewSize.width / image.width
        val onScreen = Rect(selection.topLeft * scale, selection.size * scale)

        SelectionCanvas(
            image = image,
            geometry = geometry,
            shape = shape,
            selection = selection,
            scale = scale,
            onSelectionChange = { selection = it },
            onDraggingChange = { dragging = it },
            onTapOutside = onCancel,
            modifier = Modifier.fillMaxSize(),
        )

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
                SelectionShape.entries.forEach { ShapeToggle(it, shape) { picked -> shape = picked; selection = geometry.default(picked) } }
            }
            // Directly under the shape toolbar.
            FloatingToolbar(anchor = onScreen, area = area, above = true, stackIndex = 1) {
                TextButton(onClick = { selection = geometry.max(shape, selection) }) { Text(stringResource(SelectionR.string.selection_size_max)) }
                TextButton(onClick = { selection = geometry.default(shape) }) { Text(stringResource(SelectionR.string.selection_size_default)) }
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
private fun ActionButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}

private val TOOLBAR_HEIGHT = 48.dp
private const val ABOVE_ROWS = 2
