package com.bo0sted.powerplusplus.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.data.ModuleSlot
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.withModuleAdded
import com.bo0sted.powerplusplus.data.withPageAdded
import com.bo0sted.powerplusplus.data.withPageRemoved
import com.bo0sted.powerplusplus.data.withSlotEnabled
import com.bo0sted.powerplusplus.data.withSlotMoved
import com.bo0sted.powerplusplus.data.withSlotRemoved
import com.bo0sted.powerplusplus.module.ModuleRegistry
import com.bo0sted.powerplusplus.panel.ModuleCell

/**
 * Edits pages and module placement. Tap an empty cell to add a module; tap a module to turn it on
 * or off, move it (then tap the destination cell, on any page) or remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutSection(
    config: PanelConfig,
    registry: ModuleRegistry,
    onEdit: ((PanelConfig) -> PanelConfig) -> Unit,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var movingSlotId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingToCell by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedSlotId by rememberSaveable { mutableStateOf<String?>(null) }

    val safeIndex = pageIndex.coerceIn(0, config.pages.lastIndex)
    val page = config.pages[safeIndex]
    val moving = movingSlotId?.let { id -> config.pages.flatMap { it.slots }.firstOrNull { it.id == id } }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.layout_title), style = MaterialTheme.typography.titleMedium)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(config.pages, key = { _, p -> p.id }) { index, _ ->
                    FilterChip(
                        selected = index == safeIndex,
                        onClick = { pageIndex = index },
                        label = { Text(stringResource(R.string.layout_page, index + 1)) },
                    )
                }
                item {
                    TextButton(onClick = {
                        onEdit { it.withPageAdded() }
                        pageIndex = config.pages.size
                    }) {
                        Icon(Icons.Rounded.Add, null)
                        Text(stringResource(R.string.layout_add_page))
                    }
                }
            }

            if (moving != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.layout_moving_hint),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { movingSlotId = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            }

            val byCell = page.slots.associateBy { it.cell }
            repeat(config.rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(config.columns) { column ->
                        val cell = row * config.columns + column
                        val slot = byCell[cell]
                        val onCellClick = {
                            when {
                                moving != null -> {
                                    onEdit { it.withSlotMoved(moving.id, page.id, cell) }
                                    movingSlotId = null
                                }
                                slot == null -> addingToCell = cell
                                else -> selectedSlotId = slot.id
                            }
                        }
                        Box(Modifier.weight(1f).aspectRatio(0.8f).padding(4.dp), contentAlignment = Alignment.Center) {
                            if (slot == null) {
                                EmptyCell(onClick = onCellClick, highlighted = moving != null)
                            } else {
                                val module = registry[slot.typeId]
                                ModuleCell(
                                    icon = module?.icon ?: Icons.Rounded.QuestionMark,
                                    label = module?.let { stringResource(it.title) } ?: stringResource(R.string.layout_unknown_module),
                                    active = slot.id == movingSlotId,
                                    dimmed = !slot.enabled,
                                    onClick = onCellClick,
                                    modifier = Modifier.wrapContentSize(),
                                )
                            }
                        }
                    }
                }
            }

            if (config.pages.size > 1) {
                TextButton(onClick = {
                    onEdit { it.withPageRemoved(page.id) }
                    pageIndex = (safeIndex - 1).coerceAtLeast(0)
                }) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(stringResource(R.string.layout_remove_page))
                }
            }
        }
    }

    addingToCell?.let { cell ->
        ModalBottomSheet(onDismissRequest = { addingToCell = null }) {
            Text(
                stringResource(R.string.layout_add_module),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            registry.all.forEach { module ->
                ListItem(
                    headlineContent = { Text(stringResource(module.title)) },
                    leadingContent = { Icon(module.icon, null) },
                    modifier = Modifier.clickable {
                        onEdit { it.withModuleAdded(page.id, cell, module.typeId) }
                        addingToCell = null
                    },
                )
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    selectedSlotId?.let { id ->
        val slot = config.pages.flatMap { it.slots }.firstOrNull { it.id == id }
        // The slot can vanish under an open sheet (e.g. removed); then there is nothing to show.
        if (slot != null) {
            SlotSheet(
                slot = slot,
                title = registry[slot.typeId]?.let { stringResource(it.title) } ?: stringResource(R.string.layout_unknown_module),
                onEnabled = { enabled -> onEdit { it.withSlotEnabled(slot.id, enabled) } },
                onMove = {
                    movingSlotId = slot.id
                    selectedSlotId = null
                },
                onRemove = {
                    onEdit { it.withSlotRemoved(slot.id) }
                    selectedSlotId = null
                },
                onDismiss = { selectedSlotId = null },
            )
        }
    }
}

@Composable
private fun EmptyCell(onClick: () -> Unit, highlighted: Boolean) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSheet(
    slot: ModuleSlot,
    title: String,
    onEnabled: (Boolean) -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.layout_module_enabled)) },
                trailingContent = { Switch(checked = slot.enabled, onCheckedChange = onEnabled) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.layout_move)) },
                leadingContent = { Icon(Icons.Rounded.OpenWith, null) },
                modifier = Modifier.clickable(onClick = onMove),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.layout_remove)) },
                leadingContent = { Icon(Icons.Rounded.DeleteOutline, null) },
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}
