package com.bo0sted.powerplusplus.textpick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Full-screen picker over [blocks]: each block gets a box; tapping toggles it, tapping elsewhere
 * closes. A bottom toolbar shows the selection and offers Select all, Copy and Close.
 *
 * @param onCopy receives the selected blocks' text, in reading order, one per line.
 */
@Composable
fun TextPickScreen(blocks: List<TextBlock>, onCopy: (String) -> Unit, onClose: () -> Unit) {
    val selected = remember(blocks) { mutableStateListOf<Boolean>().apply { repeat(blocks.size) { add(false) } } }
    // Block bounds are in screen pixels; this is where the window sits on screen.
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val view = LocalView.current
    val density = LocalDensity.current
    val slopPx = with(density) { 10.dp.roundToPx() }
    val corner = with(density) { 4.dp.toPx() }
    val stroke = Stroke(width = with(density) { 1.dp.toPx() })
    val selectedStroke = Stroke(width = with(density) { 2.dp.toPx() })
    val accent = MaterialTheme.colorScheme.primary
    val count = selected.count { it }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.16f))
            .onGloballyPositioned {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                origin = IntOffset(location[0], location[1])
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(blocks) {
                    detectTapGestures { tap ->
                        val hit = hitTest(blocks, tap.x.toInt() + origin.x, tap.y.toInt() + origin.y, slopPx)
                        if (hit == null) onClose() else selected[hit] = !selected[hit]
                    }
                },
        ) {
            blocks.forEachIndexed { index, block ->
                val topLeft = Offset((block.bounds.left - origin.x).toFloat(), (block.bounds.top - origin.y).toFloat())
                val size = Size(block.bounds.width.toFloat(), block.bounds.height.toFloat())
                val radius = CornerRadius(corner)
                if (selected[index]) {
                    drawRoundRect(accent.copy(alpha = 0.33f), topLeft, size, radius)
                    drawRoundRect(accent.copy(alpha = 0.87f), topLeft, size, radius, selectedStroke)
                } else {
                    drawRoundRect(Color.White.copy(alpha = 0.09f), topLeft, size, radius)
                    drawRoundRect(Color.White.copy(alpha = 0.27f), topLeft, size, radius, stroke)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                // Taps on the toolbar must not reach the canvas (which would close the picker).
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (count == 0) stringResource(R.string.textpick_hint) else pluralStringResource(R.plurals.textpick_selected, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val allSelected = count == blocks.size
                FilledTonalButton(onClick = { selected.indices.forEach { selected[it] = !allSelected } }) {
                    Text(stringResource(if (allSelected) R.string.textpick_deselect_all else R.string.textpick_select_all))
                }
                Button(
                    enabled = count > 0,
                    onClick = { onCopy(blocks.filterIndexed { i, _ -> selected[i] }.joinToString("\n") { it.text }) },
                ) { Text(stringResource(R.string.textpick_copy)) }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.textpick_close))
                }
            }
        }
    }
}
