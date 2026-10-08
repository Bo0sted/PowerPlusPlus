package com.bo0sted.powerplusplus.module.powermenu

import android.accessibilityservice.AccessibilityService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Opens the system power menu, which provides power off, restart, emergency and lockdown. */
class PowerMenuModule @Inject constructor() : ActionModule {
    override val typeId = "power_menu"
    override val title = R.string.module_power_menu
    override val icon = Icons.Rounded.PowerSettingsNew

    override suspend fun run(host: ModuleHost) {
        host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PowerMenuBindings {
    @Binds
    @IntoSet
    fun bind(module: PowerMenuModule): PanelModule
}
