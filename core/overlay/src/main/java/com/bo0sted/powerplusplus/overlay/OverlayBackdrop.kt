package com.bo0sted.powerplusplus.overlay

/** What an [OverlayWindow] does to the screen behind it. */
sealed interface OverlayBackdrop {
    data object None : OverlayBackdrop

    data class Dim(val amount: Float = 0.6f) : OverlayBackdrop

    /** Blurs everything behind the window. Falls back to [fallback] whenever the device can't blur. */
    data class Blur(val radiusPx: Int, val fallback: Dim = Dim()) : OverlayBackdrop
}
