package com.bo0sted.powerplusplus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.overlay.WindowChanges
import com.bo0sted.powerplusplus.panel.PanelController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Power++'s accessibility service. It reads nothing on its own: no event types are subscribed,
 * except window changes while an overlay waits for the keyboard ([WindowChanges]). Modules use it on
 * demand through [ModuleHost] while the user is interacting with the panel.
 */
@AndroidEntryPoint
class PowerAccessibilityService : AccessibilityService(), WindowChanges {

    @Inject lateinit var hostProvider: ModuleHostProvider
    @Inject lateinit var panel: PanelController

    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        hostProvider.set(ModuleHost(this, serviceScope))
    }

    private val windowListeners = LinkedHashSet<Runnable>()

    override fun addWindowsChangedListener(listener: Runnable) {
        if (windowListeners.add(listener) && windowListeners.size == 1) subscribeToWindowChanges(true)
    }

    override fun removeWindowsChangedListener(listener: Runnable) {
        if (windowListeners.remove(listener) && windowListeners.isEmpty()) subscribeToWindowChanges(false)
    }

    private fun subscribeToWindowChanges(on: Boolean) {
        val info = serviceInfo ?: return
        info.eventTypes = if (on) AccessibilityEvent.TYPE_WINDOWS_CHANGED else 0
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) windowListeners.toList().forEach(Runnable::run)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        panel.dismiss()
        hostProvider.set(null)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
