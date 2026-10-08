package com.bo0sted.powerplusplus.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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

    /** Closes the panel when the screen turns off, which covers both locking and sleep. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = dismiss()
    }
    private var receiverContext: Context? = null

    /** Shows the panel, or does nothing if it is already showing. Returns false if it can't be shown. */
    fun show(): Boolean {
        if (window?.isShowing == true) return true
        val host = hostProvider.host.value ?: return false
        val density = host.service.resources.displayMetrics.density
        val backdrop = when (config.value.backdrop) {
            Backdrop.Blur -> OverlayBackdrop.Blur(radiusPx = (BLUR_RADIUS_DP * density).roundToInt())
            Backdrop.Dim -> OverlayBackdrop.Dim()
        }

        val overlay = OverlayWindow(host.service, backdrop, onBack = ::dismiss)
        window = overlay
        ContextCompat.registerReceiver(
            host.service,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverContext = host.service
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
        return true
    }

    fun dismiss() {
        window?.dismiss()
        window = null
        receiverContext?.unregisterReceiver(screenOffReceiver)
        receiverContext = null
    }

    private companion object {
        const val BLUR_RADIUS_DP = 24
    }
}

internal typealias ActionHandler = (ActionModule) -> Unit
