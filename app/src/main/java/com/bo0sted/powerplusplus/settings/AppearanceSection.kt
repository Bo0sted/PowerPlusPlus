package com.bo0sted.powerplusplus.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.data.Backdrop
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.SwipeAxis
import com.bo0sted.powerplusplus.data.withGrid

@Composable
internal fun AppearanceSection(config: PanelConfig, onEdit: ((PanelConfig) -> PanelConfig) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)

            Stepper(
                label = stringResource(R.string.appearance_columns),
                value = config.columns,
                range = PanelConfig.COLUMN_RANGE,
                onChange = { columns -> onEdit { it.withGrid(columns, it.rows) } },
            )
            Stepper(
                label = stringResource(R.string.appearance_rows),
                value = config.rows,
                range = PanelConfig.ROW_RANGE,
                onChange = { rows -> onEdit { it.withGrid(it.columns, rows) } },
            )

            Text(stringResource(R.string.appearance_swipe), style = MaterialTheme.typography.titleSmall)
            Choice(
                options = listOf(SwipeAxis.Vertical to R.string.appearance_swipe_vertical, SwipeAxis.Horizontal to R.string.appearance_swipe_horizontal),
                selected = config.swipeAxis,
                onSelect = { axis -> onEdit { it.copy(swipeAxis = axis) } },
            )

            Text(stringResource(R.string.appearance_background), style = MaterialTheme.typography.titleSmall)
            Choice(
                options = listOf(Backdrop.Blur to R.string.appearance_background_blur, Backdrop.Dim to R.string.appearance_background_dim),
                selected = config.backdrop,
                onSelect = { backdrop -> onEdit { it.copy(backdrop = backdrop) } },
            )
            Text(
                stringResource(R.string.appearance_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Icon(Icons.Rounded.Remove, null) }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Icon(Icons.Rounded.Add, null) }
    }
}

@Composable
private fun <T> Choice(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}
