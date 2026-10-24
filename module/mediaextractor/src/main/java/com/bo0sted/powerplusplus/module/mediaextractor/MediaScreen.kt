package com.bo0sted.powerplusplus.module.mediaextractor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.media.regionAt
import com.bo0sted.powerplusplus.ui.MessageBar
import com.bo0sted.powerplusplus.ui.OverlayBar
import com.bo0sted.powerplusplus.ui.OverlayBarText
import com.bo0sted.powerplusplus.ui.OverlayBottomBars
import com.bo0sted.powerplusplus.ui.OverlayChoiceBar
import com.bo0sted.powerplusplus.ui.OverlayCloseButton
import com.bo0sted.powerplusplus.ui.pickedColor
import kotlin.math.roundToInt

internal enum class MediaView(val label: Int) {
    Accessibility(R.string.media_view_accessibility),
    Scan(R.string.media_view_scan),
}

/**
 * The frozen capture with a box around each region found by the current [view]. Tapping a box
 * selects it (green) or deselects it; tapping elsewhere does nothing, so a scan isn't lost to a
 * stray tap (Back and × close). At the bottom, from the top: [notice], the status/actions bar
 * (Copy only for a single selection, Save for any), the view row, and Retake in the Scan view.
 *
 * @param regions in capture pixels (the capture covers the display, as does this window); `null`
 *   while the view is still looking.
 */
@Composable
internal fun MediaScreen(
    image: ImageBitmap,
    view: MediaView,
    regions: List<IntRect>?,
    selected: Set<Int>,
    busy: Boolean,
    notice: String?,
    onToggle: (Int) -> Unit,
    onView: (MediaView) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onRetake: (() -> Unit)?,
    retaking: Boolean,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val picked = pickedColor()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = with(density) { maxWidth.toPx() } / image.width
        val slop = with(density) { 8.dp.toPx() } / scale
        val found = regions.orEmpty()

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(found, scale) {
                    detectTapGestures { tap ->
                        val point = IntOffset((tap.x / scale).roundToInt(), (tap.y / scale).roundToInt())
                        regionAt(found, point, slop.roundToInt())?.let(onToggle)
                    }
                },
        ) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val corner = CornerRadius(6.dp.toPx())
            found.forEachIndexed { index, r ->
                val topLeft = Offset(r.left * scale, r.top * scale)
                val boxSize = Size(r.width * scale, r.height * scale)
                if (index in selected) {
                    drawRoundRect(picked.copy(alpha = 0.3f), topLeft, boxSize, corner)
                    drawRoundRect(picked, topLeft, boxSize, corner, style = Stroke(3.dp.toPx()))
                } else {
                    drawRoundRect(Color.White.copy(alpha = 0.12f), topLeft, boxSize, corner)
                    drawRoundRect(Color.White.copy(alpha = 0.8f), topLeft, boxSize, corner, style = Stroke(2.dp.toPx()))
                }
            }
        }

        OverlayBottomBars {
            if (notice != null) MessageBar(notice)
            OverlayBar {
                when {
                    regions == null -> {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        OverlayBarText(stringResource(if (view == MediaView.Scan) R.string.media_scanning else R.string.media_looking))
                    }
                    regions.isEmpty() && view == MediaView.Accessibility -> {
                        OverlayBarText(stringResource(R.string.media_none_accessibility))
                        TextButton(onClick = { onView(MediaView.Scan) }) { Text(stringResource(R.string.media_try_scan)) }
                    }
                    regions.isEmpty() -> OverlayBarText(stringResource(R.string.media_none_scan))
                    selected.isEmpty() -> OverlayBarText(stringResource(R.string.media_hint))
                    else -> {
                        OverlayBarText(pluralStringResource(R.plurals.media_selected, selected.size, selected.size))
                        if (selected.size == 1) {
                            FilledTonalButton(onClick = onCopy, enabled = !busy) { Text(stringResource(R.string.media_copy)) }
                        }
                        Button(onClick = onSave, enabled = !busy) { Text(stringResource(R.string.media_save)) }
                    }
                }
                OverlayCloseButton(onClose)
            }
            OverlayChoiceBar(MediaView.entries, view, { stringResource(it.label) }, onView, enabled = !busy)
            if (view == MediaView.Scan && onRetake != null) {
                OverlayBar {
                    TextButton(onClick = onRetake, enabled = !retaking && !busy) { Text(stringResource(R.string.media_retake)) }
                }
            }
        }
    }
}
