package com.bo0sted.powerplusplus.module.screenshot.editor

import android.graphics.Bitmap

/**
 * Hands the cropped image from the selection overlay to [EditorActivity] in the same process.
 * Bitmaps are too large for an Intent. If the process dies, the image is simply gone and the
 * editor closes. Main thread only.
 */
object EditorSession {
    private var image: Bitmap? = null

    fun put(bitmap: Bitmap) {
        image?.takeIf { it !== bitmap }?.recycle()
        image = bitmap
    }

    /** Hands the image over to the caller, who then owns (and eventually recycles) it. */
    fun take(): Bitmap? = image.also { image = null }
}
