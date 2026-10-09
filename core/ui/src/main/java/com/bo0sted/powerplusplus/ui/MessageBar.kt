package com.bo0sted.powerplusplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * How long a confirmation (e.g. "Saved") stays on screen before its window closes by itself.
 * Messages are shown in Power++'s own windows instead of toasts, which Android hides while the
 * app's notifications are off.
 */
const val MESSAGE_CLOSE_DELAY_MS = 1500L

/** A short message in a rounded bar, with an optional Close button. Taps on it go nowhere else. */
@Composable
fun MessageBar(text: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = if (onClose != null) 4.dp else 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp).weight(1f, fill = false),
            )
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close)) }
            }
        }
    }
}

/**
 * A whole window that only shows [text] at the bottom, for when a feature can't start (e.g. no
 * capture). Tapping anywhere outside the bar calls [onClose], as does its Close button.
 */
@Composable
fun MessageScreen(text: String, onClose: () -> Unit, background: Color = Color.Black) {
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        MessageBar(
            text = text,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}
