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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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

/** What the text dialog is for: a new label at a point, or editing an existing label's text. */
private sealed interface TextDialog {
    data class New(val position: Offset) : TextDialog
    data class Edit(val id: Long, val text: String) : TextDialog
}

/**
 * Step 2 of Smart screenshot: annotate the cropped image. Bottom, from top to bottom: options for
 * the current tool (or the selected text), the tool row, then Close, Undo, Redo, Copy, Save, Share.
 *
 * Drawing is split into two layers so a stroke in progress never redraws everything else: the
 * image and finished marks render into a cached offscreen layer that is only redrawn when they
 * change, and the live layer on top draws just the mark being drawn, the label being moved and the
 * selection outline.
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
    var selectedLabelId by remember { mutableStateOf<Long?>(null) }
    var movingLabel by remember { mutableStateOf<Mark.Label?>(null) }
    // Which label the cached layer leaves out while it's being dragged. Separate from movingLabel,
    // which changes every frame, so the cached layer is redrawn only when a drag starts and ends.
    var hiddenLabelId by remember { mutableStateOf<Long?>(null) }
    var textDialog by remember { mutableStateOf<TextDialog?>(null) }
    var brushPreview by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val densityScope = LocalDensity.current
    val density = densityScope.density
    val marks = viewModel.history.marks
    val selectedLabel = marks.firstOrNull { it is Mark.Label && it.id == selectedLabelId } as Mark.Label?

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

    // A Surface supplies the content colour; without it icons outside the bottom panel were black on black.
    Surface(color = Color.Black, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
                // Finished marks: cached offscreen, redrawn only when the marks or effects change.
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val hidden = hiddenLabelId
                    withTransform({
                        translate(origin.x, origin.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        drawImage(image)
                        marks.forEach { mark ->
                            if (!(mark is Mark.Label && mark.id == hidden)) drawMark(mark, viewModel.effects, textMeasurer, image)
                        }
                    }
                }
                // Live layer: what is being drawn or moved right now, plus the selection outline.
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                        .pointerInput(tool, scale, origin, settings, marks) {
                            fun toImage(point: Offset) = (point - origin) / scale
                            val slop = viewConfiguration.touchSlop / scale
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = toImage(down.position)

                                val hit = marks.asReversed().filterIsInstance<Mark.Label>()
                                    .firstOrNull { labelBounds(measureLabel(textMeasurer, it, densityScope), it).contains(start) }
                                if (hit != null) {
                                    // Pressing a label selects it; dragging moves it.
                                    selectedLabelId = hit.id
                                    var moved = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val delta = toImage(change.position) - start
                                        if (moved || delta.getDistance() > slop) {
                                            moved = true
                                            hiddenLabelId = hit.id
                                            movingLabel = hit.copy(position = hit.position + delta)
                                        }
                                        change.consume()
                                    } while (change.pressed)
                                    movingLabel?.let { final -> viewModel.updateLabel(hit.id) { it.copy(position = final.position) } }
                                    movingLabel = null
                                    hiddenLabelId = null
                                    return@awaitEachGesture
                                }

                                selectedLabelId = null
                                if (tool == EditorTool.Text) {
                                    var travelled = 0f
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        travelled = maxOf(travelled, (toImage(change.position) - start).getDistance())
                                        change.consume()
                                    } while (change.pressed)
                                    if (travelled <= slop) textDialog = TextDialog.New(start)
                                    return@awaitEachGesture
                                }

                                // Marks always get an immutable copy of the points. Handing the mutable list
                                // to the state would let it change in place, after which every new copy
                                // compares equal and Compose skips the redraw (measured: 1 redraw for a
                                // 707-sample stroke).
                                val points = mutableListOf(start)
                                drawing = buildMark(tool, start, start, points.toList(), settings, imagePxPerDp)
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    // Every sample since the last frame, not just the latest, for smooth strokes.
                                    change.historical.forEach { points += toImage(it.position) }
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
                        drawing?.let { drawMark(it, viewModel.effects, textMeasurer, image, placeholderWhilePending = true) }
                        movingLabel?.let { drawMark(it, viewModel.effects, textMeasurer, image) }
                        (movingLabel ?: selectedLabel)?.let { label ->
                            val bounds = labelBounds(measureLabel(textMeasurer, label, this), label).inflate(4.dp.toPx() / scale)
                            drawRect(Color.White, bounds.topLeft, bounds.size, style = Stroke(2.dp.toPx() / scale))
                        }
                    }
                    if (brushPreview) {
                        // Shown at the brush's real on-screen size while its slider is being dragged.
                        val radius = settings.brushDp * density / 2
                        drawCircle(Color.White.copy(alpha = 0.25f), radius, center)
                        drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (selectedLabel != null) {
                        LabelOptions(
                            label = selectedLabel,
                            imagePxPerDp = imagePxPerDp,
                            update = { transform -> viewModel.updateLabel(selectedLabel.id, transform) },
                            onEditText = { textDialog = TextDialog.Edit(selectedLabel.id, selectedLabel.text) },
                            onDelete = {
                                viewModel.removeLabel(selectedLabel.id)
                                selectedLabelId = null
                            },
                        )
                    } else {
                        ToolOptions(tool, settings, viewModel::updateSettings, onBrushPreview = { brushPreview = it })
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val select: (EditorTool) -> Unit = {
                            tool = it
                            selectedLabelId = null
                        }
                        ToolButton(EditorTool.Pen, Icons.Rounded.Draw, R.string.editor_tool_pen, tool, select)
                        ToolButton(EditorTool.Arrow, Icons.Rounded.NorthEast, R.string.editor_tool_arrow, tool, select)
                        ToolButton(EditorTool.Box, Icons.Rounded.CropSquare, R.string.editor_tool_rectangle, tool, select)
                        ToolButton(EditorTool.Ellipse, Icons.Outlined.Circle, R.string.editor_tool_oval, tool, select)
                        ToolButton(EditorTool.Text, Icons.Rounded.TextFields, R.string.editor_tool_text, tool, select)
                        ToolButton(EditorTool.Blur, Icons.Rounded.BlurOn, R.string.editor_tool_blur, tool, select)
                        ToolButton(EditorTool.Pixelate, Icons.Rounded.GridOn, R.string.editor_tool_pixelate, tool, select)
                    }
                    // Actions last, at the very bottom, within thumb reach.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BarButton(Icons.Rounded.Close, R.string.editor_close, onClick = onClose)
                        Spacer(Modifier.weight(1f))
                        BarButton(Icons.AutoMirrored.Rounded.Undo, R.string.editor_undo, enabled = viewModel.history.canUndo) { viewModel.undo() }
                        BarButton(Icons.AutoMirrored.Rounded.Redo, R.string.editor_redo, enabled = viewModel.history.canRedo) { viewModel.redo() }
                        BarButton(Icons.Rounded.ContentCopy, R.string.screenshot_copy) { onOutput(EditorOutput.Copy) }
                        BarButton(Icons.Rounded.Save, R.string.screenshot_save) { onOutput(EditorOutput.Save) }
                        BarButton(Icons.Rounded.Share, R.string.screenshot_share) { onOutput(EditorOutput.Share) }
                    }
                }
            }
        }
    }

    when (val dialog = textDialog) {
        null -> Unit
        is TextDialog.New -> TextEntryDialog(
            initial = "",
            onDismiss = { textDialog = null },
            onConfirm = { text ->
                val id = viewModel.newLabelId()
                viewModel.add(Mark.Label(id, dialog.position, text, settings.color, settings.textDp * imagePxPerDp))
                selectedLabelId = id
                textDialog = null
            },
        )
        is TextDialog.Edit -> TextEntryDialog(
            initial = dialog.text,
            onDismiss = { textDialog = null },
            onConfirm = { text ->
                viewModel.updateLabel(dialog.id) { it.copy(text = text) }
                textDialog = null
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
private fun TextEntryDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_tool_text)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.focusRequester(focus)) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.editor_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

/** Options for the selected text label. Every change is a separate undo step. */
@Composable
private fun LabelOptions(
    label: Mark.Label,
    imagePxPerDp: Float,
    update: ((Mark.Label) -> Mark.Label) -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Palette(label.color) { color -> update { it.copy(color = color) } }
        SettingSlider(
            label = R.string.editor_text_size,
            value = (label.size / imagePxPerDp).coerceIn(EditorSettings.TEXT_RANGE),
            range = EditorSettings.TEXT_RANGE,
        ) { dp -> update { it.copy(size = dp * imagePxPerDp) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.editor_text_background), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = label.background, onCheckedChange = { on -> update { it.copy(background = on) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEditText) { Text(stringResource(R.string.editor_edit_text)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.editor_delete)) }
        }
    }
}
