package com.bo0sted.powerplusplus.panel

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bo0sted.powerplusplus.data.Backdrop
import com.bo0sted.powerplusplus.data.PanelConfig
import com.bo0sted.powerplusplus.data.PanelRepository
import com.bo0sted.powerplusplus.module.ModuleHost
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

    /** True while the activation screenshot is being taken and the panel isn't added yet. */
    private var capturing = false

    enum class ShowResult { Shown, SystemPowerMenu, ServiceUnavailable }

    /**
     * Shows the panel, or does nothing if it is already showing.
     *
     * The panel never appears over the lock screen. There, the system power menu opens instead —
     * through the same `PhoneWindowManager.showGlobalActionsInternal` path a plain power long-press
     * uses, so the system decides what the lock-screen menu offers. `isKeyguardLocked` is true
     * whenever the lock screen is showing, including a swipe-only one and one occluded by another
     * activity (e.g. a call).
     *
     * If the panel holds a module that uses the activation screenshot, the screen is captured first
     * and the panel is added only once the capture is back (measured at 9–32 ms on a Pixel 9 Pro XL).
     * Nothing of ours is on screen during the capture and the app underneath still has focus, so
     * apps that hide their content on focus loss are captured as the user saw them.
     */
    fun show(): ShowResult {
        if (window?.isShowing == true || capturing) return ShowResult.Shown
        val host = hostProvider.host.value ?: return ShowResult.ServiceUnavailable
        if (host.service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            return ShowResult.SystemPowerMenu
        }
        host.activationScreenshot.replace(null)
        if (!panelUsesActivationScreenshot()) {
            addPanel(host)
            return ShowResult.Shown
        }

        capturing = true
        host.service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            host.service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    // The bitmap keeps its own reference to the buffer, so ours can be closed now.
                    val bitmap = screenshot.hardwareBuffer.use { Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace) }
                    if (!capturing) {
                        bitmap?.recycle() // Dismissed (e.g. screen off) while capturing.
                        return
                    }
                    host.activationScreenshot.replace(bitmap)
                    addPanel(host)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Activation screenshot failed: $errorCode")
                    if (capturing) addPanel(host)
                }
            },
        )
        return ShowResult.Shown
    }

    private fun panelUsesActivationScreenshot(): Boolean =
        config.value.pages.any { page ->
            page.slots.any { it.enabled && registry[it.typeId]?.usesActivationScreenshot == true }
        }

    private fun addPanel(host: ModuleHost) {
        capturing = false
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
                        dismiss(keepScreenshot = module.usesActivationScreenshot)
                        host.scope.launch { module.run(host) }
                    },
                    onToggle = { module: ToggleModule, on -> host.scope.launch { module.setOn(host, on) } },
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    /**
     * Closes the panel. The activation screenshot is discarded unless [keepScreenshot] is set, which
     * is only done when handing over to a module that takes it.
     */
    fun dismiss(keepScreenshot: Boolean = false) {
        capturing = false
        window?.dismiss()
        window = null
        if (!keepScreenshot) hostProvider.host.value?.activationScreenshot?.replace(null)
    }

    private companion object {
        const val TAG = "PowerPP.Panel"
        const val BLUR_RADIUS_DP = 24
    }
}
