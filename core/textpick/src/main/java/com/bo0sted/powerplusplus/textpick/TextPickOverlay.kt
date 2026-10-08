package com.bo0sted.powerplusplus.textpick

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.ui.PowerTheme

/**
 * Shows [TextPickScreen] over everything. Copy puts the selection on the clipboard and closes.
 * Must be called on the main thread.
 */
fun showTextPick(service: AccessibilityService, blocks: List<TextBlock>) {
    if (blocks.isEmpty()) {
        Toast.makeText(service, R.string.textpick_no_text, Toast.LENGTH_SHORT).show()
        return
    }
    lateinit var window: OverlayWindow
    window = OverlayWindow(service, onDismissRequest = { window.dismiss() })
    window.show {
        PowerTheme(darkTheme = true) {
            TextPickScreen(
                blocks = blocks,
                onCopy = { text ->
                    copyToClipboard(service, text)
                    window.dismiss()
                },
                onClose = { window.dismiss() },
            )
        }
    }
}

private fun copyToClipboard(service: AccessibilityService, text: String) {
    service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Power++", text))
    // Android 13+ shows its own confirmation; a toast there would duplicate it.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(service, R.string.textpick_copied, Toast.LENGTH_SHORT).show()
    }
}
