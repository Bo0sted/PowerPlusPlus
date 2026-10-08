package com.bo0sted.powerplusplus.module.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Captures the screen again while our own overlay is showing ("Retake").
 *
 * A display capture would include the overlay, so each window is captured on its own with
 * `takeScreenshotOfWindow` — which the platform docs recommend for windows under an accessibility
 * overlay — and the captures are stacked bottom to top. The wallpaper isn't an accessibility window,
 * so it comes out black. Needs Android 14 (API 34); earlier versions have no per-window capture.
 */
internal object ScreenRecapture {

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** A software bitmap of [width]×[height] (the display), or `null` if nothing could be captured. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun capture(service: AccessibilityService, width: Int, height: Int): Bitmap? {
        // getWindows() is ordered top first; draw from the bottom up.
        val windows = service.windows
            .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .asReversed()
        val captures = windows.mapNotNull { window ->
            val bounds = Rect().also(window::getBoundsInScreen)
            captureWindow(service, window.id)?.let { it to bounds }
        }
        if (captures.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            val screen = createBitmap(width, height)
            val canvas = Canvas(screen)
            canvas.drawColor(Color.BLACK)
            captures.forEach { (hardware, bounds) ->
                // A software canvas can't draw hardware bitmaps.
                val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
                hardware.recycle()
                canvas.drawBitmap(software, bounds.left.toFloat(), bounds.top.toFloat(), null)
                software.recycle()
            }
            screen
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun captureWindow(service: AccessibilityService, windowId: Int): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            service.takeScreenshotOfWindow(
                windowId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        // The bitmap keeps its own reference to the buffer, so ours can be closed now.
                        val bitmap = screenshot.hardwareBuffer.use { Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace) }
                        continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) = continuation.resume(null)
                },
            )
        }
}
