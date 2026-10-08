package com.bo0sted.powerplusplus.module.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.ui.PowerTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Crop the screen as it was when Power++ was activated (rectangle, circle or edge-to-edge strip),
 * then copy, save or share it.
 */
class SmartScreenshotModule @Inject constructor() : ActionModule {
    override val typeId = "smart_screenshot"
    override val title = R.string.module_smart_screenshot
    override val icon = Icons.Rounded.Screenshot
    override val usesActivationScreenshot = true

    override suspend fun run(host: ModuleHost) {
        val service = host.service
        val captured = host.activationScreenshot.take() ?: return service.toast(R.string.screenshot_capture_failed)
        val image = withContext(Dispatchers.Default) {
            ScreenshotImage.toSoftware(captured).also { captured.recycle() }
        }
        if (withContext(Dispatchers.Default) { ScreenshotImage.looksBlocked(image) }) {
            image.recycle()
            return service.toast(R.string.screenshot_blocked)
        }
        showSelection(host, image)
    }

    private fun showSelection(host: ModuleHost, image: Bitmap) {
        val service = host.service
        lateinit var window: OverlayWindow
        var busy = false
        fun close() {
            window.dismiss()
            image.recycle()
        }
        fun finish(action: ScreenshotAction, selection: Rect, shape: SelectionShape) {
            if (busy) return
            busy = true
            host.scope.launch {
                val cropped = withContext(Dispatchers.Default) { ScreenshotImage.crop(image, selection, shape) }
                when (action) {
                    ScreenshotAction.Copy -> {
                        close()
                        val copied = withContext(Dispatchers.IO) { ScreenshotOutput.copy(service, cropped) }
                        // Android 13+ shows its own clipboard confirmation.
                        if (!copied) service.toast(R.string.screenshot_failed)
                        else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) service.toast(R.string.screenshot_copied)
                    }
                    ScreenshotAction.Save -> {
                        close()
                        val saved = withContext(Dispatchers.IO) { ScreenshotOutput.save(service, cropped) }
                        service.toast(if (saved) R.string.screenshot_saved else R.string.screenshot_failed)
                    }
                    ScreenshotAction.Share -> {
                        val intent = withContext(Dispatchers.IO) { ScreenshotOutput.shareIntent(service, cropped) }
                        // Started while our overlay is still visible: a visible system-type window is
                        // what allows an accessibility service to start an activity.
                        if (intent == null) service.toast(R.string.screenshot_failed) else service.startActivity(intent)
                        close()
                    }
                }
                cropped.recycle()
            }
        }

        window = OverlayWindow(service, onDismissRequest = ::close)
        window.show {
            PowerTheme(darkTheme = true) {
                SelectScreen(image = image.asImageBitmap(), onAction = ::finish, onCancel = ::close)
            }
        }
    }
}

private fun AccessibilityService.toast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SmartScreenshotBindings {
    @Binds
    @IntoSet
    fun bind(module: SmartScreenshotModule): PanelModule
}
