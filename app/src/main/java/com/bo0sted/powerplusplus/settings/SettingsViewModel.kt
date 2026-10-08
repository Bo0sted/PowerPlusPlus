package com.bo0sted.powerplusplus.settings

import android.app.role.RoleManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.PanelRepository
import com.bo0sted.powerplusplus.module.ModuleRegistry
import com.bo0sted.powerplusplus.panel.PanelController
import com.bo0sted.powerplusplus.service.ModuleHostProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PanelRepository,
    private val panel: PanelController,
    hostProvider: ModuleHostProvider,
    val registry: ModuleRegistry,
) : ViewModel() {

    /** `null` until the stored layout has been read. */
    val config: StateFlow<PanelConfig?> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accessibilityConnected: StateFlow<Boolean> =
        hostProvider.host.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val assistantRole = MutableStateFlow(false)
    val isAssistant: StateFlow<Boolean> = assistantRole.asStateFlow()

    /** Role changes happen in system Settings, so this is re-checked whenever the screen resumes. */
    fun refreshAssistantRole() {
        assistantRole.value = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    fun edit(transform: (PanelConfig) -> PanelConfig) {
        viewModelScope.launch { repository.edit(transform) }
    }

    fun openPanel(): Boolean = panel.show()
}
