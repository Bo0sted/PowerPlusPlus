package com.bo0sted.powerplusplus.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.download.ComponentState
import com.bo0sted.powerplusplus.ntfy.NtfyServer
import com.bo0sted.powerplusplus.ntfy.NtfySettings

/**
 * The Whisper assistant: its speech model, the microphone, ntfy (its own switchable part, which
 * commands may depend on) and the list of commands with a switch each.
 */
@Composable
internal fun AssistantSection(viewModel: AssistantViewModel = hiltViewModel()) {
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val microphone by viewModel.microphoneGranted.collectAsStateWithLifecycle()
    val ntfy by viewModel.ntfy.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshMicrophone() }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleMedium)
            Body(stringResource(R.string.assistant_body))

            viewModel.modelCatalog.forEach { model ->
                ComponentRow(
                    name = stringResource(R.string.assistant_model_name, model.version),
                    source = stringResource(R.string.assistant_model_source),
                    downloadBytes = model.downloadBytes,
                    state = modelStates[model.id] ?: ComponentState.NotInstalled,
                    onInstall = { viewModel.installModel(model) },
                    onRemove = { viewModel.removeModel(model) },
                )
            }

            Microphone(microphone, onResult = { viewModel.refreshMicrophone() })

            HorizontalDivider()
            ntfy?.let { settings ->
                NtfyPart(
                    settings = settings,
                    busy = viewModel.ntfyBusy,
                    error = viewModel.ntfyError,
                    onEnabledChange = viewModel::setNtfyEnabled,
                    onSave = viewModel::saveNtfy,
                    onRefreshLimit = viewModel::refreshNtfyLimit,
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.assistant_commands), style = MaterialTheme.typography.titleSmall)
            viewModel.allCommands.forEach { command ->
                val on by remember(command) { viewModel.isOn(command) }.collectAsState(initial = false)
                val available by command.available.collectAsState(initial = false)
                SwitchRow(
                    title = stringResource(command.title),
                    body = stringResource(command.description) +
                        if (available) "" else "\n" + stringResource(command.requirement),
                    checked = on,
                    enabled = available,
                    onCheckedChange = { viewModel.setOn(command, it) },
                )
            }
        }
    }
}

@Composable
private fun Microphone(granted: Boolean, onResult: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        onResult()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.assistant_microphone), style = MaterialTheme.typography.titleSmall)
            Body(stringResource(if (granted) R.string.assistant_microphone_granted else R.string.assistant_microphone_needed))
        }
        if (!granted) {
            FilledTonalButton(onClick = {
                // Once Android stops asking (after a refusal), only App info can grant it.
                if (asked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) { Text(stringResource(R.string.assistant_microphone_allow)) }
        }
    }
}

@Composable
private fun NtfyPart(
    settings: NtfySettings,
    busy: Boolean,
    error: String?,
    onEnabledChange: (Boolean) -> Unit,
    onSave: (url: String, topic: String, token: String) -> Unit,
    onRefreshLimit: () -> Unit,
) {
    SwitchRow(
        title = stringResource(R.string.ntfy_title),
        body = stringResource(R.string.ntfy_body),
        checked = settings.enabled,
        enabled = true,
        onCheckedChange = onEnabledChange,
    )
    if (!settings.enabled) return

    val saved = settings.server
    // Edits stay local until a test post succeeds; the saved values are the starting point.
    var url by rememberSaveable(saved) { mutableStateOf(saved?.url ?: DEFAULT_SERVER) }
    var topic by rememberSaveable(saved) { mutableStateOf(saved?.topic.orEmpty()) }
    var token by rememberSaveable(saved) { mutableStateOf(saved?.token.orEmpty()) }
    val changed = saved == null || url != saved.url || topic != saved.topic || token != saved.token

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.ntfy_server)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text(stringResource(R.string.ntfy_topic)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.ntfy_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(stringResource(R.string.ntfy_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (saved != null && !changed) SavedServer(saved)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (saved != null && !changed) {
                TextButton(onClick = onRefreshLimit, enabled = !busy) { Text(stringResource(R.string.ntfy_refresh_limit)) }
            }
            Button(onClick = { onSave(url, topic, token) }, enabled = !busy && changed && topic.isNotBlank()) {
                Text(stringResource(R.string.ntfy_save))
            }
        }
    }
}

@Composable
private fun SavedServer(server: NtfyServer) {
    val seconds = server.delayLimitSeconds
    val limit = when {
        seconds % 86_400 == 0L -> pluralStringResource(R.plurals.ntfy_days, (seconds / 86_400).toInt(), (seconds / 86_400).toInt())
        seconds % 3_600 == 0L -> pluralStringResource(R.plurals.ntfy_hours, (seconds / 3_600).toInt(), (seconds / 3_600).toInt())
        else -> pluralStringResource(R.plurals.ntfy_minutes, (seconds / 60).toInt(), (seconds / 60).toInt())
    }
    Body(stringResource(R.string.ntfy_saved, "${server.url.removePrefix("https://")}/${server.topic}", limit))
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Body(body)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val DEFAULT_SERVER = "https://ntfy.sh"
