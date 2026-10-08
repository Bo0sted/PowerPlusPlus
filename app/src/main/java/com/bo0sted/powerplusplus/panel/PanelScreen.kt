package com.bo0sted.powerplusplus.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.PanelPage
import com.bo0sted.powerplusplus.data.SwipeAxis
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.ModuleRegistry
import com.bo0sted.powerplusplus.module.ToggleModule

/**
 * The power panel: pages of module grids over a blurred or dimmed backdrop. Tapping anywhere that
 * isn't a module dismisses it.
 */
@Composable
internal fun PanelScreen(
    config: PanelConfig,
    registry: ModuleRegistry,
    host: ModuleHost,
    onAction: (ActionModule) -> Unit,
    onToggle: (ToggleModule, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { config.pages.size })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .safeDrawingPadding(),
    ) {
        val page: @Composable (Int) -> Unit = { index ->
            PanelPageGrid(config, config.pages[index], registry, host, onAction, onToggle)
        }
        when (config.swipeAxis) {
            SwipeAxis.Vertical -> VerticalPager(pagerState, Modifier.fillMaxSize()) { page(it) }
            SwipeAxis.Horizontal -> HorizontalPager(pagerState, Modifier.fillMaxSize()) { page(it) }
        }
        PageIndicator(pagerState, config.swipeAxis, Modifier.align(
            if (config.swipeAxis == SwipeAxis.Vertical) Alignment.CenterEnd else Alignment.BottomCenter,
        ))
    }
}

@Composable
private fun PanelPageGrid(
    config: PanelConfig,
    page: PanelPage,
    registry: ModuleRegistry,
    host: ModuleHost,
    onAction: (ActionModule) -> Unit,
    onToggle: (ToggleModule, Boolean) -> Unit,
) {
    val byCell = page.slots.filter { it.enabled }.associateBy { it.cell }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        repeat(config.rows) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                repeat(config.columns) { column ->
                    val cellModifier = Modifier.weight(1f).wrapContentSize()
                    val module = byCell[row * config.columns + column]?.let { registry[it.typeId] }
                    when (module) {
                        is ActionModule -> ModuleCell(
                            icon = module.icon,
                            label = stringResource(module.title),
                            active = false,
                            onClick = { onAction(module) },
                            modifier = cellModifier,
                        )
                        is ToggleModule -> {
                            val on by remember(module) { module.isOn(host) }.collectAsState(initial = false)
                            ModuleCell(
                                icon = module.icon,
                                label = stringResource(module.title),
                                active = on,
                                onClick = { onToggle(module, !on) },
                                modifier = cellModifier,
                            )
                        }
                        // Empty cell, or a module that is no longer installed: leave the space.
                        null -> Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(state: PagerState, axis: SwipeAxis, modifier: Modifier) {
    if (state.pageCount < 2) return
    val dots: @Composable () -> Unit = {
        repeat(state.pageCount) { index ->
            val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (index == state.currentPage) 0.9f else 0.3f)
            Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
        }
    }
    if (axis == SwipeAxis.Vertical) {
        Column(modifier.padding(8.dp)) { dots() }
    } else {
        Row(modifier.padding(8.dp)) { dots() }
    }
}
