package com.bo0sted.powerplusplus.overlay

import android.accessibilityservice.AccessibilityService
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.bo0sted.powerplusplus.ui.MessageScreen
import com.bo0sted.powerplusplus.ui.PowerTheme

/**
 * Opens a window that only shows [message], for a feature that can't start (no capture, a blocked
 * app). Tapping anywhere, its Close button, Back or the screen turning off closes it. Main thread only.
 */
fun showMessageWindow(service: AccessibilityService, @StringRes message: Int) {
    lateinit var window: OverlayWindow
    window = OverlayWindow(service, onDismissRequest = { window.dismiss() })
    window.show {
        PowerTheme(darkTheme = true) {
            MessageScreen(stringResource(message), onClose = { window.dismiss() })
        }
    }
}
