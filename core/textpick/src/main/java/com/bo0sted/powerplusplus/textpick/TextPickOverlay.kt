package com.bo0sted.powerplusplus.textpick

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.ui.MESSAGE_CLOSE_DELAY_MS
import com.bo0sted.powerplusplus.ui.MessageScreen
import com.bo0sted.powerplusplus.ui.PowerTheme

/**
 * Shows [TextPickScreen] over everything. Copy puts the selection on the clipboard and closes.
 * With no [blocks], the window only says so. Messages are shown in the window rather than as toasts,
 * which Android hides while notifications are off. Must be called on the main thread.
 */
fun showTextPick(service: AccessibilityService, blocks: List<TextBlock>) {
    lateinit var window: OverlayWindow
    val state = TextPickState(blocks)
    var message by mutableStateOf(if (blocks.isEmpty()) R.string.textpick_no_text else null)
    window = OverlayWindow(
        service,
        onDismissRequest = { window.dismiss() },
        onBackRequest = { if (state.editing != null) state.cancelEdit() else window.dismiss() },
    )
    window.show {
        PowerTheme(darkTheme = true) {
            val shown = message
            if (shown != null) {
                // Over the live screen, like the picker itself.
                MessageScreen(stringResource(shown), onClose = { window.dismiss() }, background = Color.Black.copy(alpha = 0.16f))
            } else {
                TextPickScreen(
                    state = state,
                    onCopy = { text ->
                        if (copyToClipboard(service, text)) {
                            message = R.string.textpick_copied
                            Handler(Looper.getMainLooper()).postDelayed({ window.dismiss() }, MESSAGE_CLOSE_DELAY_MS)
                        } else {
                            window.dismiss()
                        }
                    },
                    onClose = { window.dismiss() },
                    onEditingChange = window::setTyping,
                )
            }
        }
    }
}

/**
 * Puts [text] on the clipboard. Returns whether the caller should confirm it ("Copied"): Android 13+
 * shows its own confirmation, which the platform's guidance says not to duplicate.
 */
fun copyToClipboard(service: AccessibilityService, text: String): Boolean {
    service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Power++", text))
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
}
