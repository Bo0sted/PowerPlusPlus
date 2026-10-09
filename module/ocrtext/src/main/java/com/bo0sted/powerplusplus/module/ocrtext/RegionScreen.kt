package com.bo0sted.powerplusplus.module.ocrtext

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.module.ocrtext.Stage.Problem
import com.bo0sted.powerplusplus.module.ocrtext.Stage.Recognizing
import com.bo0sted.powerplusplus.module.ocrtext.Stage.Select
import com.bo0sted.powerplusplus.selection.SelectionCanvas
import com.bo0sted.powerplusplus.selection.SelectionShape
import com.bo0sted.powerplusplus.selection.ShapeToggle
import com.bo0sted.powerplusplus.selection.rememberSelectionGeometry
import com.bo0sted.powerplusplus.textpick.TextPickState
import com.bo0sted.powerplusplus.selection.R as SelectionR

/** What the OCR window shows. */
internal sealed interface Stage {
    /** Choosing the region; [notice] is shown above Confirm (e.g. after a failed Retake). */
    data class Select(@param:StringRes val notice: Int? = null) : Stage
    data object Recognizing : Stage
    data class Results(val state: TextPickState) : Stage
    data class Problem(@param:StringRes val message: Int, val action: ProblemAction? = null) : Stage
}

internal enum class ProblemAction(@param:StringRes val label: Int) {
    TryAgain(R.string.ocr_try_again),
    OpenSettings(R.string.ocr_open_settings),
}

/** Text is read in rectangular lines; a circle would only cut words off at its edges. */
private val SHAPES = listOf(SelectionShape.Rectangle, SelectionShape.HorizontalStrip, SelectionShape.VerticalStrip)

/**
 * The frozen capture with Smart screenshot's resizable selection, starting at the same default.
 * Two centred rows at the bottom: shapes, Max and Default; under them Retake (when [onRetake] is
 * given) and Confirm. Both hide while dragging; tapping outside the selection closes.
 *
 * Also shows [Stage.Recognizing] (the selection with a progress bar) and [Stage.Problem].
 *
 * @param image the capture, display-sized; `null` if there is none (shown as black).
 * @param onConfirm receives the selection in capture pixels.
 */
@Composable
internal fun RegionScreen(
    stage: Stage,
    image: ImageBitmap?,
    onConfirm: (Rect) -> Unit,
    onRetake: (() -> Unit)?,
    retaking: Boolean,
    onProblemAction: (ProblemAction) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (image != null) {
            val geometry = rememberSelectionGeometry(image)
            var shape by remember { mutableStateOf(SelectionShape.Rectangle) }
            var selection by remember(geometry) { mutableStateOf(geometry.default(SelectionShape.Rectangle)) }
            var dragging by remember { mutableStateOf(false) }
            // The capture covers the whole display, as does this window; scale only guards rounding.
            val scale = with(density) { maxWidth.toPx() } / image.width

            SelectionCanvas(
                image = image,
                geometry = geometry,
                shape = shape,
                selection = selection,
                scale = scale,
                onSelectionChange = { selection = it },
                onDraggingChange = { dragging = it },
                onTapOutside = onClose,
                modifier = Modifier.fillMaxSize(),
                interactive = stage is Select,
            )

            if (stage is Select && !dragging) {
                BottomBars {
                    Bar(Modifier.align(Alignment.CenterHorizontally)) {
                        SHAPES.forEach { ShapeToggle(it, shape) { picked -> shape = picked; selection = geometry.default(picked) } }
                        TextButton(onClick = { selection = geometry.max(shape, selection) }) { Text(stringResource(SelectionR.string.selection_size_max)) }
                        TextButton(onClick = { selection = geometry.default(shape) }) { Text(stringResource(SelectionR.string.selection_size_default)) }
                    }
                    Bar(Modifier.align(Alignment.CenterHorizontally)) {
                        stage.notice?.let { BarText(it) }
                        if (onRetake != null) {
                            TextButton(onClick = onRetake, enabled = !retaking) { Text(stringResource(R.string.ocr_retake)) }
                        }
                        Button(onClick = { onConfirm(selection) }) { Text(stringResource(R.string.ocr_confirm)) }
                    }
                }
            }
        }

        when (stage) {
            Recognizing -> BottomBars {
                Bar(Modifier.align(Alignment.CenterHorizontally)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    BarText(R.string.ocr_recognizing)
                    CloseButton(onClose)
                }
            }
            is Problem -> BottomBars {
                Bar(Modifier.align(Alignment.CenterHorizontally)) {
                    BarText(stage.message)
                    stage.action?.let { action ->
                        TextButton(onClick = { onProblemAction(action) }) { Text(stringResource(action.label)) }
                    }
                    CloseButton(onClose)
                }
            }
            is Select, is Stage.Results -> Unit // Select is above; Results are shown by TextPickScreen.
        }
    }
}

@Composable
private fun BoxScope.BottomBars(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Bar(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        // Taps on a bar must not reach the canvas (which would close the window).
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun RowScope.BarText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
    )
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ocr_close)) }
}
