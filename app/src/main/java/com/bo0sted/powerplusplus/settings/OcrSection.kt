package com.bo0sted.powerplusplus.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.bo0sted.powerplusplus.ocr.ComponentState
import com.bo0sted.powerplusplus.ocr.OcrCatalog
import com.bo0sted.powerplusplus.ocr.OcrComponent

/** Download, check and remove the OCR engine and models. Nothing OCR-related ships in the APK. */
@Composable
internal fun OcrSection(
    states: Map<String, ComponentState>,
    onInstall: (OcrComponent) -> Unit,
    onRemove: (OcrComponent) -> Unit,
) {
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ocr_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ocr_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OcrCatalog.ALL.forEach { component ->
                ComponentRow(
                    component = component,
                    state = states[component.id] ?: ComponentState.NotInstalled,
                    onInstall = { onInstall(component) },
                    onRemove = { confirmRemove = component.id },
                )
            }
        }
    }

    OcrCatalog.ALL.firstOrNull { it.id == confirmRemove }?.let { component ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.ocr_remove_title, componentName(component))) },
            text = { Text(stringResource(R.string.ocr_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(component)
                    confirmRemove = null
                }) { Text(stringResource(R.string.ocr_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ComponentRow(component: OcrComponent, state: ComponentState, onInstall: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(componentName(component), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(if (component.kind == OcrComponent.Kind.Engine) R.string.ocr_engine_source else R.string.ocr_model_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val status = when (state) {
            ComponentState.NotInstalled -> stringResource(R.string.ocr_status_not_installed, size(component.downloadBytes))
            is ComponentState.Downloading -> stringResource(
                R.string.ocr_status_downloading,
                (state.bytes * 100 / state.total).toInt(),
                size(state.bytes),
                size(state.total),
            )
            ComponentState.Verifying -> stringResource(R.string.ocr_status_verifying)
            is ComponentState.Installed -> stringResource(R.string.ocr_status_installed, size(state.bytesOnDisk))
            is ComponentState.Failed -> stringResource(R.string.ocr_status_failed, state.reason)
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
                ComponentState.NotInstalled -> FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.ocr_download)) }
                is ComponentState.Failed -> FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.ocr_retry)) }
                is ComponentState.Downloading -> TextButton(onClick = onRemove) { Text(stringResource(R.string.action_cancel)) }
                ComponentState.Verifying -> Unit
                is ComponentState.Installed -> TextButton(onClick = onRemove) { Text(stringResource(R.string.ocr_remove)) }
            }
        }
    }
}

@Composable
private fun componentName(component: OcrComponent): String =
    if (component.kind == OcrComponent.Kind.Engine) stringResource(R.string.ocr_engine_name, component.version)
    else component.version
