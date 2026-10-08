package com.bo0sted.powerplusplus.module

import android.graphics.Bitmap

/**
 * The screen as it was when Power++ was activated, captured before the panel appeared. It lives only
 * in memory and has a single owner: the panel holds it while open, and a module that uses it
 * [take]s it (and must then recycle it). Main thread only.
 */
class ActivationScreenshot {
    private var bitmap: Bitmap? = null

    /** Hands the screenshot over to the caller, or returns `null` if there is none. */
    fun take(): Bitmap? = bitmap.also { bitmap = null }

    /** Replaces the held screenshot, recycling the previous one. Pass `null` to discard. */
    fun replace(new: Bitmap?) {
        bitmap?.takeIf { it !== new }?.recycle()
        bitmap = new
    }
}
