package com.bo0sted.powerplusplus.module

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope

/**
 * What a module runs against: the connected accessibility service, which provides global actions,
 * the window tree, screenshots and a [android.view.WindowManager] for accessibility overlays.
 *
 * [scope] lives as long as the service connection.
 */
class ModuleHost(
    val service: AccessibilityService,
    val scope: CoroutineScope,
) {
    val activationScreenshot = ActivationScreenshot()
}
