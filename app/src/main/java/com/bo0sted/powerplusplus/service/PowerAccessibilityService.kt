package com.bo0sted.powerplusplus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.panel.PanelController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Power++'s accessibility service. It reads nothing on its own (no event types are subscribed);
 * modules use it on demand through [ModuleHost] while the user is interacting with the panel.
 */
@AndroidEntryPoint
class PowerAccessibilityService : AccessibilityService() {

    @Inject lateinit var hostProvider: ModuleHostProvider
    @Inject lateinit var panel: PanelController

    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        hostProvider.set(ModuleHost(this, serviceScope))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        panel.dismiss()
        hostProvider.set(null)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
