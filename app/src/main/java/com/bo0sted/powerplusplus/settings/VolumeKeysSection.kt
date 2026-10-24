package com.bo0sted.powerplusplus.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.data.KeyShortcuts
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.VolumeShortcuts
import com.bo0sted.powerplusplus.module.ModuleRegistry
import kotlin.math.roundToInt

/**
 * Volume key shortcuts: a module for each key's press and double press, and the double-press
 * window. Any module type can be chosen, whether or not it's on a page.
 */
@Composable
internal fun VolumeKeysSection(config: PanelConfig, registry: ModuleRegistry, onEdit: ((PanelConfig) -> PanelConfig) -> Unit) {
    val shortcuts = config.volumeShortcuts
    fun edit(transform: (VolumeShortcuts) -> VolumeShortcuts) = onEdit { it.copy(volumeShortcuts = transform(it.volumeShortcuts)) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.volume_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.volume_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeyRows(R.string.volume_up, shortcuts.up, registry) { change -> edit { it.copy(up = change(it.up)) } }
            KeyRows(R.string.volume_down, shortcuts.down, registry) { change -> edit { it.copy(down = change(it.down)) } }
            WindowSlider(shortcuts.doublePressWindowMs) { ms -> edit { it.copy(doublePressWindowMs = ms) } }
        }
    }
}

@Composable
private fun KeyRows(title: Int, key: KeyShortcuts, registry: ModuleRegistry, onEdit: ((KeyShortcuts) -> KeyShortcuts) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    ModulePicker(stringResource(R.string.volume_press), key.press, registry) { typeId -> onEdit { it.copy(press = typeId) } }
    ModulePicker(stringResource(R.string.volume_double_press), key.doublePress, registry) { typeId -> onEdit { it.copy(doublePress = typeId) } }
}

/** A module type, or None. A type no longer in the build shows as unavailable until changed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModulePicker(label: String, selected: String?, registry: ModuleRegistry, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = when (selected) {
        null -> stringResource(R.string.volume_none)
        else -> registry[selected]?.let { stringResource(it.title) } ?: stringResource(R.string.layout_unknown_module)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.volume_none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            registry.all.forEach { module ->
                DropdownMenuItem(
                    text = { Text(stringResource(module.title)) },
                    leadingIcon = { Icon(module.icon, contentDescription = null) },
                    onClick = {
                        onSelect(module.typeId)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Saved on release, not on every step of the drag. */
@Composable
private fun WindowSlider(windowMs: Int, onChange: (Int) -> Unit) {
    val range = VolumeShortcuts.WINDOW_RANGE_MS
    var dragging by remember(windowMs) { mutableFloatStateOf(windowMs.toFloat()) }
    Text(stringResource(R.string.volume_window, dragging.roundToInt()), style = MaterialTheme.typography.titleSmall)
    Slider(
        value = dragging,
        onValueChange = { dragging = it },
        onValueChangeFinished = { onChange(dragging.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / WINDOW_STEP_MS - 1,
    )
    Text(
        stringResource(R.string.volume_window_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val WINDOW_STEP_MS = 50
