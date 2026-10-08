package com.bo0sted.powerplusplus.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R

@Composable
internal fun SetupSection(
    accessibilityConnected: Boolean,
    isAssistant: Boolean,
    onTryPanel: () -> Unit,
) {
    val context = LocalContext.current
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)

            SetupStep(
                done = accessibilityConnected,
                title = stringResource(R.string.setup_accessibility_title),
                body = stringResource(R.string.setup_accessibility_body),
            ) {
                FilledTonalButton(onClick = { context.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(stringResource(R.string.setup_accessibility_action))
                }
                TextButton(onClick = {
                    context.launch(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text(stringResource(R.string.setup_app_info_action)) }
            }

            SetupStep(
                done = isAssistant,
                title = stringResource(R.string.setup_assistant_title),
                body = stringResource(R.string.setup_assistant_body),
            ) {
                FilledTonalButton(onClick = { context.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }) {
                    Text(stringResource(R.string.setup_assistant_action))
                }
            }

            SetupStep(
                done = null,
                title = stringResource(R.string.setup_power_button_title),
                body = stringResource(R.string.setup_power_button_body),
            )

            Button(onClick = onTryPanel, enabled = accessibilityConnected) {
                Text(stringResource(R.string.setup_try_panel))
            }
        }
    }
}

/** [done] is `null` for a step the app can't verify. */
@Composable
private fun SetupStep(done: Boolean?, title: String, body: String, actions: @Composable () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (done) {
            true -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            false -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            null -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.outline)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (done != true) Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** Settings screens may be missing on some builds; never crash over it. */
private fun Context.launch(intent: Intent) {
    runCatching { startActivity(intent) }
}
