package com.bo0sted.powerplusplus.panel

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bo0sted.powerplusplus.data.Backdrop
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.PanelRepository
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleRegistry
import com.bo0sted.powerplusplus.module.ToggleModule
import com.bo0sted.powerplusplus.overlay.OverlayBackdrop
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.service.ModuleHostProvider
import com.bo0sted.powerplusplus.ui.PowerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Shows and dismisses the power panel. All calls must be made on the main thread. */
@Singleton
class PanelController @Inject constructor(
    private val hostProvider: ModuleHostProvider,
    private val registry: ModuleRegistry,
    repository: PanelRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Kept hot so the panel opens instantly with the latest layout instead of waiting on disk. */
    private val config: StateFlow<PanelConfig> =
        repository.config.stateIn(scope, SharingStarted.Eagerly, PanelConfig())

    private var window: OverlayWindow? = null

    enum class ShowResult { Shown, SystemPowerMenu, ServiceUnavailable }

    /**
     * Shows the panel, or does nothing if it is already showing.
     *
     * The panel never appears over the lock screen. There, the system power menu opens instead —
     * through the same `PhoneWindowManager.showGlobalActionsInternal` path a plain power long-press
     * uses, so the system decides what the lock-screen menu offers. `isKeyguardLocked` is true
     * whenever the lock screen is showing, including a swipe-only one and one occluded by another
     * activity (e.g. a call).
     */
    fun show(): ShowResult {
        if (window?.isShowing == true) return ShowResult.Shown
        val host = hostProvider.host.value ?: return ShowResult.ServiceUnavailable
        if (host.service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            return ShowResult.SystemPowerMenu
        }
        val density = host.service.resources.displayMetrics.density
        val backdrop = when (config.value.backdrop) {
            Backdrop.Blur -> OverlayBackdrop.Blur(radiusPx = (BLUR_RADIUS_DP * density).roundToInt())
            Backdrop.Dim -> OverlayBackdrop.Dim()
        }

        val overlay = OverlayWindow(host.service, backdrop, onDismissRequest = ::dismiss)
        window = overlay
        overlay.show {
            val current by config.collectAsStateWithLifecycle()
            PowerTheme(darkTheme = true) {
                PanelScreen(
                    config = current,
                    registry = registry,
                    host = host,
                    onAction = { module ->
                        dismiss()
                        host.scope.launch { module.run(host) }
                    },
                    onToggle = { module: ToggleModule, on -> host.scope.launch { module.setOn(host, on) } },
                    onDismiss = ::dismiss,
                )
            }
        }
        return ShowResult.Shown
    }

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    private companion object {
        const val BLUR_RADIUS_DP = 24
    }
}

internal typealias ActionHandler = (ActionModule) -> Unit
