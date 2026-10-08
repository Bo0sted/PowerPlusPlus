package com.bo0sted.powerplusplus.module

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow

/**
 * A feature that can be placed in a cell of the power panel.
 *
 * Implementations live in their own `:module:*` Gradle module and are contributed to the app with a
 * Hilt `@IntoSet` binding of [PanelModule]. Nothing outside the implementing module refers to a
 * concrete module type; the panel and the editor work only through this interface.
 */
sealed interface PanelModule {
    /** Stable identifier persisted in the user's layout. Never change it once released. */
    val typeId: String

    @get:StringRes
    val title: Int

    val icon: ImageVector
}

/** A module that performs an action when tapped. The panel is dismissed before [run] is called. */
interface ActionModule : PanelModule {
    suspend fun run(host: ModuleHost)
}

/** A module with an on/off state. The panel stays open when it is toggled. */
interface ToggleModule : PanelModule {
    fun isOn(host: ModuleHost): Flow<Boolean>

    suspend fun setOn(host: ModuleHost, on: Boolean)
}
