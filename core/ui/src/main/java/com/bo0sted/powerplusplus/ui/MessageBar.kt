package com.bo0sted.powerplusplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

/**
 * The rounded pill that holds controls and messages at the bottom of Power++'s overlays. Taps on it
 * never reach what's underneath (which might close the window or pick something).
 */
@Composable
fun OverlayBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
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

/** Stacks [OverlayBar]s at the bottom centre of an overlay, above the navigation bar. */
@Composable
fun BoxScope.OverlayBottomBars(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Secondary text inside an [OverlayBar]; it gives way to the bar's buttons when space is short. */
@Composable
fun RowScope.OverlayBarText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 4.dp).weight(1f, fill = false),
    )
}

/** The × that closes an overlay. */
@Composable
fun OverlayCloseButton(onClose: () -> Unit) {
    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close)) }
}

/**
 * An [OverlayBar] of mutually exclusive [choices] as segmented buttons, e.g. an overlay's views or
 * selection modes. [label] names each choice.
 */
@Composable
fun <T> OverlayChoiceBar(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    OverlayBar {
        SingleChoiceSegmentedButtonRow {
            choices.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                ) { Text(label(choice)) }
            }
        }
    }
}

/** A short message in an [OverlayBar], with an optional Close button. */
@Composable
fun MessageBar(text: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    OverlayBar(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).weight(1f, fill = false),
        )
        if (onClose != null) OverlayCloseButton(onClose)
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
