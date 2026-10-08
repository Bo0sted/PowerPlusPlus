package com.bo0sted.powerplusplus.overlay

/** What an [OverlayWindow] does to the screen behind it. */
sealed interface OverlayBackdrop {
    data object None : OverlayBackdrop

    data class Dim(val amount: Float = 0.6f) : OverlayBackdrop

    /**
     * Blurs everything behind the window, with a light [dimAmount] on top. Falls back to [fallback]
     * whenever the device can't blur.
     *
     * The dim must be non-zero: on a Pixel 9 Pro XL (Android 17) a fully transparent blur layer was
     * composited by the display hardware, which can't blur, so it showed in screenshots (always
     * GPU-composited) but not on screen. The defaults match the AOSP window-blurs guidance
     * (0.1 with blur, 0.4 without).
     */
    data class Blur(val radiusPx: Int, val dimAmount: Float = 0.1f, val fallback: Dim = Dim(0.4f)) : OverlayBackdrop
}
