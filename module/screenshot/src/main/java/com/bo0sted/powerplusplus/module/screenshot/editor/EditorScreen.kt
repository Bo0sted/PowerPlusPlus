package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bo0sted.powerplusplus.module.screenshot.R
import kotlin.math.min

enum class EditorTool { Pen, Arrow, Box, Ellipse, Text, Blur, Pixelate }

enum class EditorOutput { Copy, Save, Share }

private val PALETTE = listOf(
    0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0, 0xFFFFFFFF, 0xFF000000,
).map { it.toInt() }

/**
 * Step 2 of Smart screenshot: annotate the cropped image. Top bar: Close, Undo, Redo, Copy, Save,
 * Share. Bottom: options for the current tool above the tool row.
 */
@Composable
internal fun EditorScreen(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onOutput: (EditorOutput) -> Unit,
) {
    val image = viewModel.image ?: return
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var tool by remember { mutableStateOf(EditorTool.Pen) }
    var drawing by remember { mutableStateOf<Mark?>(null) }
    var pendingLabel by remember { mutableStateOf<Offset?>(null) }
    var brushPreview by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    // The image is fitted into the view; these convert between view and image pixels.
    val scale = if (viewSize == IntSize.Zero) 1f else min(viewSize.width.toFloat() / image.width, viewSize.height.toFloat() / image.height)
    val origin = Offset((viewSize.width - image.width * scale) / 2, (viewSize.height - image.height * scale) / 2)
    val imagePxPerDp = density / scale

    // Keep the processed image for the active effect tool ready before the user starts painting.
    LaunchedEffect(tool, settings.blurStrength, settings.pixelateStrength) {
        when (tool) {
            EditorTool.Blur -> viewModel.prepareEffect(EffectKind.Blur, settings.blurStrength)
            EditorTool.Pixelate -> viewModel.prepareEffect(EffectKind.Pixelate, settings.pixelateStrength)
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BarButton(Icons.Rounded.Close, R.string.editor_close, onClick = onClose)
            Spacer(Modifier.weight(1f))
            BarButton(Icons.AutoMirrored.Rounded.Undo, R.string.editor_undo, enabled = viewModel.history.canUndo) { viewModel.undo() }
            BarButton(Icons.AutoMirrored.Rounded.Redo, R.string.editor_redo, enabled = viewModel.history.canRedo) { viewModel.redo() }
            BarButton(Icons.Rounded.ContentCopy, R.string.screenshot_copy) { onOutput(EditorOutput.Copy) }
            BarButton(Icons.Rounded.Save, R.string.screenshot_save) { onOutput(EditorOutput.Save) }
            BarButton(Icons.Rounded.Share, R.string.screenshot_share) { onOutput(EditorOutput.Share) }
        }

        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewSize = it }
                .pointerInput(tool, scale, origin, settings) {
                    fun toImage(point: Offset) = (point - origin) / scale
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = toImage(down.position)
                        if (tool == EditorTool.Text) {
                            pendingLabel = start
                            return@awaitEachGesture
                        }
                        val points = mutableListOf(start)
                        drawing = buildMark(tool, start, start, points, settings, imagePxPerDp)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChange() != Offset.Zero) {
                                val current = toImage(change.position)
                                points += current
                                drawing = buildMark(tool, start, current, points.toList(), settings, imagePxPerDp)
                            }
                            change.consume()
                        } while (change.pressed)
                        drawing?.takeIf { it.isMeaningful() }?.let(viewModel::add)
                        drawing = null
                    }
                },
        ) {
            withTransform({
                translate(origin.x, origin.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawEdits(image, viewModel.history.marks + listOfNotNull(drawing), viewModel.effects, textMeasurer)
            }
            if (brushPreview) {
                // Shown at the brush's real on-screen size while its slider is being dragged.
                val radius = settings.brushDp * density / 2
                drawCircle(Color.White.copy(alpha = 0.25f), radius, center)
                drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                ToolOptions(tool, settings, viewModel::updateSettings, onBrushPreview = { brushPreview = it })
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolButton(EditorTool.Pen, Icons.Rounded.Draw, R.string.editor_tool_pen, tool) { tool = it }
                    ToolButton(EditorTool.Arrow, Icons.Rounded.NorthEast, R.string.editor_tool_arrow, tool) { tool = it }
                    ToolButton(EditorTool.Box, Icons.Rounded.CropSquare, R.string.editor_tool_rectangle, tool) { tool = it }
                    ToolButton(EditorTool.Ellipse, Icons.Outlined.Circle, R.string.editor_tool_oval, tool) { tool = it }
                    ToolButton(EditorTool.Text, Icons.Rounded.TextFields, R.string.editor_tool_text, tool) { tool = it }
                    ToolButton(EditorTool.Blur, Icons.Rounded.BlurOn, R.string.editor_tool_blur, tool) { tool = it }
                    ToolButton(EditorTool.Pixelate, Icons.Rounded.GridOn, R.string.editor_tool_pixelate, tool) { tool = it }
                }
            }
        }
    }

    pendingLabel?.let { position ->
        TextEntryDialog(
            onDismiss = { pendingLabel = null },
            onConfirm = { text ->
                viewModel.add(Mark.Label(position, text, settings.color, settings.textDp * imagePxPerDp))
                pendingLabel = null
            },
        )
    }
}

/**
 * The mark for a drag from [start] to [current] through [points], with sizes converted from the
 * settings' dp to image pixels.
 */
private fun buildMark(
    tool: EditorTool,
    start: Offset,
    current: Offset,
    points: List<Offset>,
    settings: EditorSettings,
    imagePxPerDp: Float,
): Mark? {
    val stroke = settings.strokeDp * imagePxPerDp
    return when (tool) {
        EditorTool.Pen -> Mark.Pen(points, settings.color, stroke)
        EditorTool.Arrow -> Mark.Arrow(start, current, settings.color, stroke)
        EditorTool.Box -> Mark.Box(rectBetween(start, current), settings.color, stroke)
        EditorTool.Ellipse -> Mark.Ellipse(rectBetween(start, current), settings.color, stroke)
        EditorTool.Blur, EditorTool.Pixelate -> {
            val blur = tool == EditorTool.Blur
            val area = when (settings.effectArea) {
                EffectAreaMode.Brush -> EffectArea.Brush(points, settings.brushDp * imagePxPerDp)
                EffectAreaMode.Rectangle -> EffectArea.Box(rectBetween(start, current))
            }
            Mark.Effect(
                kind = if (blur) EffectKind.Blur else EffectKind.Pixelate,
                strength = if (blur) settings.blurStrength else settings.pixelateStrength,
                area = area,
            )
        }
        EditorTool.Text -> null
    }
}

/** Drops accidental taps for tools where a tap means nothing (a pen tap still draws a dot). */
private fun Mark.isMeaningful(): Boolean = when (this) {
    is Mark.Pen -> true
    is Mark.Arrow -> (end - start).getDistance() > width * 2
    is Mark.Box -> rect.width > width && rect.height > width
    is Mark.Ellipse -> rect.width > width && rect.height > width
    is Mark.Effect -> when (area) {
        is EffectArea.Brush -> true
        is EffectArea.Box -> area.rect.width > 4f && area.rect.height > 4f
    }
    is Mark.Label -> text.isNotBlank()
}

@Composable
private fun ToolOptions(
    tool: EditorTool,
    settings: EditorSettings,
    update: ((EditorSettings) -> EditorSettings) -> Unit,
    onBrushPreview: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (tool) {
            EditorTool.Pen, EditorTool.Arrow, EditorTool.Box, EditorTool.Ellipse -> {
                Palette(settings.color) { color -> update { it.copy(color = color) } }
                SettingSlider(R.string.editor_width, settings.strokeDp, EditorSettings.STROKE_RANGE) { v -> update { it.copy(strokeDp = v) } }
            }
            EditorTool.Text -> {
                Palette(settings.color) { color -> update { it.copy(color = color) } }
                SettingSlider(R.string.editor_text_size, settings.textDp, EditorSettings.TEXT_RANGE) { v -> update { it.copy(textDp = v) } }
            }
            EditorTool.Blur, EditorTool.Pixelate -> {
                val blur = tool == EditorTool.Blur
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    EffectAreaMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.effectArea == mode,
                            onClick = { update { it.copy(effectArea = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, EffectAreaMode.entries.size),
                        ) {
                            Text(stringResource(if (mode == EffectAreaMode.Brush) R.string.editor_area_brush else R.string.editor_area_rectangle))
                        }
                    }
                }
                SettingSlider(
                    label = R.string.editor_strength,
                    value = if (blur) settings.blurStrength else settings.pixelateStrength,
                    range = 0f..1f,
                ) { v -> update { if (blur) it.copy(blurStrength = v) else it.copy(pixelateStrength = v) } }
                if (settings.effectArea == EffectAreaMode.Brush) {
                    SettingSlider(
                        label = R.string.editor_brush_size,
                        value = settings.brushDp,
                        range = EditorSettings.BRUSH_RANGE,
                        onDragging = onBrushPreview,
                    ) { v -> update { it.copy(brushDp = v) } }
                }
            }
        }
    }
}

/**
 * A labelled slider that saves only when released ([onCommit]), so dragging doesn't write to disk
 * on every frame. [onDragging] reports whether the thumb is being held.
 */
@Composable
private fun SettingSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDragging: (Boolean) -> Unit = {},
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(dragged || pressed) { onDragging(dragged || pressed) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            interactionSource = interaction,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Palette(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        PALETTE.forEach { color ->
            Box(
                Modifier
                    .size(32.dp)
                    .border(
                        BorderStroke(if (color == selected) 3.dp else 1.dp, if (color == selected) MaterialTheme.colorScheme.primary else Color.Gray),
                        CircleShape,
                    )
                    .padding(4.dp)
                    .background(Color(color), CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}

@Composable
private fun ToolButton(value: EditorTool, icon: ImageVector, label: Int, current: EditorTool, onSelect: (EditorTool) -> Unit) {
    IconToggleButton(checked = value == current, onCheckedChange = { onSelect(value) }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = stringResource(label)) }
}

@Composable
private fun TextEntryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_tool_text)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.focusRequester(focus)) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.editor_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}
