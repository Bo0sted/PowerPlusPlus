package com.bo0sted.powerplusplus.media

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Entry points for finding the pictures and videos on screen. */
object MediaFinder {

    /**
     * Smallest side a region needs to count as media, so icons, avatars and emoji aren't offered.
     * 72 dp is larger than the usual 40–56 dp icons and avatars and smaller than feed thumbnails.
     */
    const val MIN_SIDE_DP = 72

    fun minSidePx(context: Context): Int = (MIN_SIDE_DP * context.resources.displayMetrics.density).roundToInt()

    /** What apps report through accessibility (exact bounds, live screen). */
    suspend fun accessibility(service: AccessibilityService): List<IntRect> =
        withContext(Dispatchers.Default) { ScreenMediaFinder.find(service, minSidePx(service)) }

    /** What the pixels of [image], a software display-sized capture, show. */
    suspend fun scan(context: Context, image: Bitmap): List<IntRect> =
        withContext(Dispatchers.Default) { MediaScan.scan(image, minSidePx(context)) }

    /** Both, without near-duplicates; the apps' own bounds win, being exact. */
    suspend fun all(service: AccessibilityService, image: Bitmap): List<IntRect> =
        distinctRegions(accessibility(service) + scan(service, image))
}
