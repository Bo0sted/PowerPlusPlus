package com.bo0sted.powerplusplus.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.assistant.AssistantSession.Status
import com.bo0sted.powerplusplus.ui.MessageBar
import com.bo0sted.powerplusplus.ui.OverlayBar
import com.bo0sted.powerplusplus.ui.OverlayBarText
import com.bo0sted.powerplusplus.ui.onBackdrop
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hosts the panel [content] with the assistant: the voice wave at the bottom and, for a recognised
 * command, the command modal on top. The panel stays usable while the assistant listens.
 *
 * While a text field in the modal has focus, only the modal is drawn and [onCompactChange] asks the
 * window to shrink to it, so the keyboard (drawn below accessibility overlays) is visible.
 */
@Composable
fun AssistantOverlay(
    session: AssistantSession,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompactChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val draft = session.modal.draft
    val compact = draft != null && session.editingText
    LaunchedEffect(compact) { onCompactChange(compact) }

    Box(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
        if (!compact) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            ) {
                Box(Modifier.weight(1f)) { content() }
                if (session.status != Status.Off) VoiceBar(session, onOpenSettings)
            }
            if (draft != null) {
                // Keeps the panel from being used underneath; a stray tap mustn't lose the command.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { } })
            }
        }
        // Always the last child, so the modal (and its focused field) survives switching to compact.
        if (draft != null) PanelCommandModal(session, draft, compact)
    }
}

@Composable
private fun VoiceBar(session: AssistantSession, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        session.notice?.let { notice ->
            notice.heard?.let { heard ->
                Text(
                    stringResource(R.string.assistant_heard, heard),
                    style = MaterialTheme.typography.bodyMedium.onBackdrop(),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            MessageBar(stringResource(notice.message), Modifier.padding(horizontal = 16.dp))
        }
        when (val status = session.status) {
            Status.Off -> Unit
            Status.NeedsMicrophone -> OverlayBar(Modifier.padding(horizontal = 16.dp)) {
                OverlayBarText(stringResource(R.string.assistant_needs_microphone))
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.assistant_open_settings)) }
            }
            is Status.Failed -> MessageBar(stringResource(R.string.assistant_failed, status.reason), Modifier.padding(horizontal = 16.dp))
            Status.Listening, Status.Transcribing -> {
                val transcribing = status == Status.Transcribing
                Box(Modifier.fillMaxWidth().height(WAVE_HEIGHT), contentAlignment = Alignment.Center) {
                    VoiceWave(level = if (transcribing) 0f else session.level, busy = transcribing, modifier = Modifier.fillMaxSize())
                    WhisperOrb(level = session.level, busy = transcribing)
                }
                Text(
                    stringResource(if (transcribing) R.string.assistant_transcribing else R.string.assistant_listening),
                    style = MaterialTheme.typography.labelLarge.onBackdrop(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Layered sine waves across the screen, swelling with the voice; a gentle ripple while [busy]. */
@Composable
private fun VoiceWave(level: Float, busy: Boolean, modifier: Modifier) {
    val smoothed by animateFloatAsState(level, tween(durationMillis = 90), label = "level")
    val time by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = if (busy) 900 else 1_800, easing = LinearEasing)),
        label = "phase",
    )
    val colors = MaterialTheme.colorScheme
    val layers = remember(colors) {
        listOf(
            WaveLayer(cycles = 1.5f, speed = 1f, height = 1f, color = colors.primary),
            WaveLayer(cycles = 2.2f, speed = -1f, height = 0.7f, color = colors.tertiary),
            WaveLayer(cycles = 3.1f, speed = 2f, height = 0.45f, color = colors.secondary),
        )
    }
    Canvas(modifier.graphicsLayer { alpha = 0.95f }) {
        val swell = if (busy) 0.12f else 0.06f + smoothed * 0.6f
        layers.forEach { layer ->
            val amplitude = size.height / 2 * swell * layer.height
            val path = wavePath(layer.cycles, time * layer.speed, amplitude)
            // A soft glow under a crisp line.
            drawPath(path, layer.color.copy(alpha = 0.18f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, layer.color.copy(alpha = 0.9f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private class WaveLayer(val cycles: Float, val speed: Float, val height: Float, val color: Color)

/** A sine of [cycles] periods across the width, tapered to flat at both edges. */
private fun DrawScope.wavePath(cycles: Float, phase: Float, amplitude: Float): Path {
    val path = Path()
    val step = 3.dp.toPx()
    var x = 0f
    while (x <= size.width) {
        val t = x / size.width
        val envelope = sin(PI * t).let { (it * it).toFloat() }
        val y = size.height / 2 + amplitude * envelope * sin(2 * PI * cycles * t + phase).toFloat()
        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
        x += step
    }
    return path
}

/** Power++'s whisper mark: three breath strokes in a glowing orb that pulses with the voice. */
@Composable
private fun WhisperOrb(level: Float, busy: Boolean) {
    val smoothed by animateFloatAsState(level, tween(durationMillis = 120), label = "orb")
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing)),
        label = "pulse",
    )
    val colors = MaterialTheme.colorScheme
    val scale = 1f + 0.18f * smoothed + if (busy) 0.05f * sin(2 * PI * pulse).toFloat() else 0f
    Box(
        Modifier
            .size(ORB_SIZE)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.primary, colors.tertiary)))
            .pointerInput(Unit) { detectTapGestures { } }, // Not a dismissal tap.
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ORB_SIZE * 0.6f)) {
            val unit = size.minDimension / 24f
            val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
            // Three short waves, each a little shorter and further right: a breath fading out.
            listOf(Triple(7f, 4f, 20f), Triple(12f, 6f, 19f), Triple(17f, 8f, 17f)).forEach { (y, start, end) ->
                val path = Path()
                var x = start
                while (x <= end) {
                    val py = y + 1.4f * sin(2 * PI * (x - start) / (end - start)).toFloat()
                    if (x == start) path.moveTo(x * unit, py * unit) else path.lineTo(x * unit, py * unit)
                    x += 0.5f
                }
                drawPath(path, colors.onPrimary, style = stroke)
            }
            drawCircle(colors.onPrimary, radius = 1.3f * unit, center = Offset(3f * unit, 7f * unit))
        }
    }
}

/** The [CommandModal] at the top of the panel; it tells the session when a text field has focus. */
@Composable
private fun PanelCommandModal(session: AssistantSession, draft: CommandDraft, compact: Boolean) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(session.clearFocusRequests) { if (session.clearFocusRequests > 0) focusManager.clearFocus() }
    // The display's height, not the window's: the window shrinks to the modal while typing.
    val screenHeight = with(LocalDensity.current) { LocalResources.current.displayMetrics.heightPixels.toDp() }
    CommandModal(
        state = session.modal,
        draft = draft,
        onConfirm = session::confirmDraft,
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            // Compact: leave the lower half for the keyboard.
            .heightIn(max = screenHeight * if (compact) 0.45f else 0.8f)
            .onFocusChanged { session.editingText = it.hasFocus }
            // Taps on the card don't reach the scrim; a tap outside a field ends typing.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    )
}

private val WAVE_HEIGHT = 112.dp
private val ORB_SIZE = 64.dp
