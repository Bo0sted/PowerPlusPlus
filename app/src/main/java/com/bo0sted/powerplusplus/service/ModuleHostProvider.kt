package com.bo0sted.powerplusplus.service

import com.bo0sted.powerplusplus.module.ModuleHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the [ModuleHost] while [PowerAccessibilityService] is connected; `null` otherwise. */
@Singleton
class ModuleHostProvider @Inject constructor() {
    private val current = MutableStateFlow<ModuleHost?>(null)

    val host: StateFlow<ModuleHost?> = current.asStateFlow()

    internal fun set(host: ModuleHost?) {
        current.value = host
    }
}
