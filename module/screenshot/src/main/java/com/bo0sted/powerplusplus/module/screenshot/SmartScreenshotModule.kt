package com.bo0sted.powerplusplus.module.screenshot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import com.bo0sted.powerplusplus.capture.ScreenImage
import com.bo0sted.powerplusplus.capture.ScreenRecapture
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import com.bo0sted.powerplusplus.module.screenshot.editor.EditorActivity
import com.bo0sted.powerplusplus.module.screenshot.editor.EditorSession
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.selection.SelectionShape
import com.bo0sted.powerplusplus.ui.MESSAGE_CLOSE_DELAY_MS
import com.bo0sted.powerplusplus.ui.MessageScreen
import com.bo0sted.powerplusplus.ui.PowerTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Crop the screen as it was when Power++ was activated (rectangle, circle or edge-to-edge strip),
 * then copy, save or share it. Problems and confirmations are shown in the window itself rather than
 * as toasts, which Android hides while notifications are off.
 */
class SmartScreenshotModule @Inject constructor() : ActionModule {
    override val typeId = "smart_screenshot"
    override val title = R.string.module_smart_screenshot
    override val icon = Icons.Rounded.Screenshot
    override val usesActivationScreenshot = true

    override suspend fun run(host: ModuleHost) {
        val captured = host.activationScreenshot.take()
        val image = captured?.let { withContext(Dispatchers.Default) { ScreenImage.toSoftware(it).also { captured.recycle() } } }
        when {
            image == null -> showMessage(host, R.string.screenshot_capture_failed)
            withContext(Dispatchers.Default) { ScreenImage.looksBlocked(image) } -> {
                image.recycle()
                showMessage(host, R.string.screenshot_blocked)
            }
            else -> showSelection(host, image)
        }
    }

    /** A window with only [message], for when there is nothing to select from. */
    private fun showMessage(host: ModuleHost, message: Int) {
        lateinit var window: OverlayWindow
        window = OverlayWindow(host.service, onDismissRequest = { window.dismiss() })
        window.show {
            PowerTheme(darkTheme = true) {
                MessageScreen(stringResource(message), onClose = { window.dismiss() })
            }
        }
    }

    private fun showSelection(host: ModuleHost, initial: Bitmap) {
        val service = host.service
        lateinit var window: OverlayWindow
        // While an action runs or a confirmation shows, the toolbars hide and the selection is fixed.
        var busy by mutableStateOf(false)
        // Replaced by Retake; the frozen activation capture is the first value.
        var image by mutableStateOf(initial)
        var retaking by mutableStateOf(false)
        var notice by mutableStateOf<Int?>(null)
        fun close() {
            if (!window.isShowing) return
            window.dismiss()
            image.recycle()
        }
        /** Shows [message], then closes; for successes the user should still see confirmed. */
        fun confirmAndClose(message: Int) {
            notice = message
            host.scope.launch {
                delay(MESSAGE_CLOSE_DELAY_MS)
                close()
            }
        }
        fun retake() {
            if (retaking || !ScreenRecapture.isSupported) return
            retaking = true
            notice = null
            host.scope.launch {
                val fresh = ScreenRecapture.capture(service, image.width, image.height)
                when {
                    fresh == null -> notice = R.string.screenshot_capture_failed
                    withContext(Dispatchers.Default) { ScreenImage.looksBlocked(fresh) } -> {
                        fresh.recycle()
                        notice = R.string.screenshot_blocked
                    }
                    !window.isShowing -> fresh.recycle()
                    // The replaced image may still be referenced by a frame being rendered, so it is
                    // left to the garbage collector rather than recycled here.
                    else -> image = fresh
                }
                retaking = false
            }
        }
        fun finish(action: ScreenshotAction, selection: Rect, shape: SelectionShape) {
            if (busy) return
            busy = true
            notice = null
            host.scope.launch {
                val cropped = withContext(Dispatchers.Default) { ScreenshotImage.crop(image, selection, shape) }
                // A failure keeps the window open so the user can try again.
                var failed = false
                when (action) {
                    ScreenshotAction.Copy -> {
                        val copied = withContext(Dispatchers.IO) { ScreenshotOutput.copy(service, cropped) }
                        when {
                            !copied -> failed = true
                            // Android 13+ shows its own clipboard confirmation.
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> confirmAndClose(R.string.screenshot_copied)
                            else -> close()
                        }
                    }
                    ScreenshotAction.Save -> {
                        val saved = withContext(Dispatchers.IO) { ScreenshotOutput.save(service, cropped) }
                        if (saved) confirmAndClose(R.string.screenshot_saved) else failed = true
                    }
                    ScreenshotAction.Edit -> {
                        // The editor takes ownership of the crop. Started while our overlay is still
                        // visible, which is what allows the activity start (see Share).
                        EditorSession.put(cropped)
                        service.startActivity(Intent(service, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        close()
                        return@launch
                    }
                    ScreenshotAction.Share -> {
                        val intent = withContext(Dispatchers.IO) { ScreenshotOutput.shareIntent(service, cropped) }
                        // Started while our overlay is still visible: a visible system-type window is
                        // what allows an accessibility service to start an activity.
                        if (intent == null) {
                            failed = true
                        } else {
                            service.startActivity(intent)
                            close()
                        }
                    }
                }
                cropped.recycle()
                if (failed) {
                    notice = R.string.screenshot_failed
                    busy = false
                }
            }
        }

        window = OverlayWindow(service, onDismissRequest = ::close)
        window.show {
            PowerTheme(darkTheme = true) {
                SelectScreen(
                    image = image.asImageBitmap(),
                    onAction = ::finish,
                    onCancel = ::close,
                    onRetake = if (ScreenRecapture.isSupported) ::retake else null,
                    retaking = retaking,
                    notice = notice?.let { stringResource(it) },
                    busy = busy,
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SmartScreenshotBindings {
    @Binds
    @IntoSet
    fun bind(module: SmartScreenshotModule): PanelModule
}
