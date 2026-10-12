package com.bo0sted.powerplusplus.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.download.ComponentState

/**
 * One downloadable engine or model: where it comes from, its state, and Download / Cancel / Retry /
 * Remove. Removing asks first.
 */
@Composable
internal fun ComponentRow(
    name: String,
    source: String,
    downloadBytes: Long,
    state: ComponentState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val status = when (state) {
            ComponentState.NotInstalled -> stringResource(R.string.component_status_not_installed, size(downloadBytes))
            is ComponentState.Downloading -> stringResource(
                R.string.component_status_downloading,
                (state.bytes * 100 / state.total).toInt(),
                size(state.bytes),
                size(state.total),
            )
            ComponentState.Verifying -> stringResource(R.string.component_status_verifying)
            is ComponentState.Installed -> stringResource(R.string.component_status_installed, size(state.bytesOnDisk))
            is ComponentState.Failed -> stringResource(R.string.component_status_failed, state.reason)
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state is ComponentState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        when (state) {
            is ComponentState.Downloading -> LinearProgressIndicator(
                progress = { state.bytes.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            ComponentState.Verifying -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> Unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                ComponentState.NotInstalled -> FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.component_download)) }
                is ComponentState.Failed -> FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.component_retry)) }
                is ComponentState.Downloading -> TextButton(onClick = onRemove) { Text(stringResource(R.string.action_cancel)) }
                ComponentState.Verifying -> Unit
                is ComponentState.Installed -> TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.component_remove)) }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.component_remove_title, name)) },
            text = { Text(stringResource(R.string.component_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    confirmRemove = false
                }) { Text(stringResource(R.string.component_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
