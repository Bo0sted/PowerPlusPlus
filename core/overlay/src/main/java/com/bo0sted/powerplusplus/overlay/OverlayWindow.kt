package com.bo0sted.powerplusplus.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.function.Consumer

/**
 * A full-screen `TYPE_ACCESSIBILITY_OVERLAY` window hosting Compose content.
 *
 * It must be created from the connected [AccessibilityService]: only that service's own
 * [WindowManager] carries the token that lets it add accessibility overlays, which is why Power++
 * needs no "display over other apps" permission. One instance shows once; create a new one each time.
 *
 * @param onDismissRequest called when the user presses or swipes Back, or when the screen turns off
 *   (which covers both locking and sleep). The owner decides how to dismiss.
 */
class OverlayWindow(
    private val service: AccessibilityService,
    private val backdrop: OverlayBackdrop = OverlayBackdrop.None,
    private val onDismissRequest: () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val blurListener = Consumer<Boolean> { enabled -> applyBackdrop(blurAvailable = enabled) }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onDismissRequest()
    }
    private var root: OverlayRoot? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    val isShowing: Boolean get() = root != null

    fun show(content: @Composable () -> Unit) {
        check(root == null && lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            "An OverlayWindow can be shown only once"
        }
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val compose = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        val overlayRoot = OverlayRoot(service, onDismissRequest).apply {
            addView(compose)
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
        }
        root = overlayRoot
        windowManager.addView(overlayRoot, layoutParams())
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // The system delivers its own broadcasts to non-exported receivers (BroadcastSkipPolicy).
        ContextCompat.registerReceiver(
            service,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (backdrop is OverlayBackdrop.Blur) {
            // Called immediately with the current state, then on every change (e.g. battery saver).
            windowManager.addCrossWindowBlurEnabledListener(service.mainExecutor, blurListener)
        }
    }

    fun dismiss() {
        val current = root ?: return
        root = null
        service.unregisterReceiver(screenOffReceiver)
        if (backdrop is OverlayBackdrop.Blur) windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        // The system may already have removed the window (for example on screen off).
        if (current.isAttachedToWindow) windowManager.removeViewImmediate(current)
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Windows added through WindowManager don't inherit hardware acceleration; Compose needs it.
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Draw under the status and navigation bars; content applies insets itself.
        fitInsetsTypes = 0
        when (backdrop) {
            OverlayBackdrop.None -> Unit
            is OverlayBackdrop.Dim -> dim(backdrop)
            // The first blur listener callback switches to the fallback if blur is unavailable.
            is OverlayBackdrop.Blur -> blur(backdrop)
        }
    }

    private fun applyBackdrop(blurAvailable: Boolean) {
        val current = root ?: return
        val blur = backdrop as? OverlayBackdrop.Blur ?: return
        val params = current.layoutParams as WindowManager.LayoutParams
        if (blurAvailable) params.blur(blur) else params.dim(blur.fallback)
        if (current.isAttachedToWindow) windowManager.updateViewLayout(current, params)
    }

    private fun WindowManager.LayoutParams.blur(blur: OverlayBackdrop.Blur) {
        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        blurBehindRadius = blur.radiusPx
        dimAmount = blur.dimAmount
    }

    private fun WindowManager.LayoutParams.dim(dim: OverlayBackdrop.Dim) {
        flags = (flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND) and
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        blurBehindRadius = 0
        dimAmount = dim.amount
    }
}

/**
 * Root view that delivers Back to [onBack]. On API 33+ Back arrives through the window's
 * [OnBackInvokedDispatcher] (the manifest opts in with `enableOnBackInvokedCallback`, and it is the
 * only path for targetSdk 36+ on Android 16+), so views never see `KEYCODE_BACK` there. API 31–32
 * only have the key event.
 */
@SuppressLint("ViewConstructor") // Created only in code, never inflated.
private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {

    private val backCallback: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OnBackInvokedCallback { onBack() } else null

    /** The dispatcher the callback was registered with; looked up once while attached. */
    private var backDispatcher: Any? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val dispatcher = findOnBackInvokedDispatcher() ?: return
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback as OnBackInvokedCallback,
            )
            backDispatcher = dispatcher
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backDispatcher as? OnBackInvokedDispatcher)
                ?.unregisterOnBackInvokedCallback(backCallback as OnBackInvokedCallback)
            backDispatcher = null
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
