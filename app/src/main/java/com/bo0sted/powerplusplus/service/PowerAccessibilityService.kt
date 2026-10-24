package com.bo0sted.powerplusplus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.overlay.KeyEvents
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
 * except window changes while an overlay waits for the keyboard ([WindowChanges]), and no key events
 * are requested except while the panel has volume key shortcuts ([KeyEvents]). Modules use it on
 * demand through [ModuleHost] while the user is interacting with the panel.
 */
@AndroidEntryPoint
class PowerAccessibilityService : AccessibilityService(), WindowChanges, KeyEvents {

    @Inject lateinit var hostProvider: ModuleHostProvider
    @Inject lateinit var panel: PanelController

    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        hostProvider.set(ModuleHost(this, serviceScope))
    }

    private val windowListeners = LinkedHashSet<Runnable>()

    private var keyEventFilter: ((KeyEvent) -> Boolean)? = null

    override fun addWindowsChangedListener(listener: Runnable) {
        if (windowListeners.add(listener) && windowListeners.size == 1) updateServiceInfo()
    }

    override fun removeWindowsChangedListener(listener: Runnable) {
        if (windowListeners.remove(listener) && windowListeners.isEmpty()) updateServiceInfo()
    }

    override fun setKeyEventFilter(filter: ((KeyEvent) -> Boolean)?) {
        val changed = (filter == null) != (keyEventFilter == null)
        keyEventFilter = filter
        if (changed) updateServiceInfo()
    }

    /** Asks the system for exactly what's needed right now: window changes and key events, or neither. */
    private fun updateServiceInfo() {
        val info = serviceInfo ?: return
        info.eventTypes = if (windowListeners.isEmpty()) 0 else AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.flags = if (keyEventFilter == null) {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        } else {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = keyEventFilter?.invoke(event) ?: false

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
