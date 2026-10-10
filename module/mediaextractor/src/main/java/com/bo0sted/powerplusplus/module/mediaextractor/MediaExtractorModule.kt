package com.bo0sted.powerplusplus.module.mediaextractor

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import com.bo0sted.powerplusplus.capture.ScreenImage
import com.bo0sted.powerplusplus.capture.ScreenRecapture
import com.bo0sted.powerplusplus.media.ImageOutput
import com.bo0sted.powerplusplus.media.MediaFinder
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.overlay.showMessageWindow
import com.bo0sted.powerplusplus.ui.MESSAGE_CLOSE_DELAY_MS
import com.bo0sted.powerplusplus.ui.PowerTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Picks the pictures and videos out of the screen as it was when Power++ was activated, and copies
 * or saves them. Two views: **Accessibility** (the default) uses the bounds apps report; **Scan**
 * finds media from the pixels, on demand. What's saved is the capture cut to each region, at screen
 * resolution; for a video, the frame on screen. Messages are shown in the window, never as toasts.
 */
class MediaExtractorModule @Inject constructor() : ActionModule {
    override val typeId = "media_extractor"
    override val title = R.string.module_media_extractor
    override val icon = Icons.Rounded.PhotoLibrary
    override val usesActivationScreenshot = true

    override suspend fun run(host: ModuleHost) {
        val captured = host.activationScreenshot.take()
        val image = captured?.let { withContext(Dispatchers.Default) { ScreenImage.toSoftware(it).also { captured.recycle() } } }
        when {
            image == null -> showMessageWindow(host.service, R.string.media_capture_failed)
            withContext(Dispatchers.Default) { ScreenImage.looksBlocked(image) } -> {
                image.recycle()
                showMessageWindow(host.service, R.string.media_blocked)
            }
            else -> show(host, image)
        }
    }

    private fun show(host: ModuleHost, initial: Bitmap) {
        val service = host.service
        lateinit var window: OverlayWindow
        // Replaced by Retake; the frozen activation capture is the first value.
        var image by mutableStateOf(initial)
        var view by mutableStateOf(MediaView.Accessibility)
        // `null` while still looking.
        var fromAccessibility by mutableStateOf<List<IntRect>?>(null)
        var fromScan by mutableStateOf<List<IntRect>?>(null)
        var selected by mutableStateOf(emptySet<Int>())
        var busy by mutableStateOf(false)
        var retaking by mutableStateOf(false)
        var notice by mutableStateOf<String?>(null)
        // Work that reads the image; it's recycled only once these are over.
        val jobs = mutableListOf<Job>()

        fun launchWork(block: suspend () -> Unit) {
            jobs.removeAll { !it.isActive }
            jobs += host.scope.launch { block() }
        }

        fun close() {
            if (!window.isShowing) return
            window.dismiss()
            val last = image
            val running = jobs.toList()
            host.scope.launch {
                running.forEach { it.join() }
                last.recycle()
            }
        }

        fun confirmAndClose(message: String) {
            notice = message
            host.scope.launch {
                delay(MESSAGE_CLOSE_DELAY_MS)
                close()
            }
        }

        fun startScan() {
            if (fromScan != null) return
            val source = image
            launchWork {
                val found = MediaFinder.scan(service, source)
                if (image === source) fromScan = found
            }
        }

        fun selectView(next: MediaView) {
            if (busy) return
            view = next
            selected = emptySet()
            notice = null
            if (next == MediaView.Scan) startScan()
        }

        fun regions(): List<IntRect> = (if (view == MediaView.Accessibility) fromAccessibility else fromScan).orEmpty()

        /** The selected regions cut out of the capture, in selection order of the list. */
        suspend fun crops(): List<Bitmap> {
            val source = image
            val chosen = regions().filterIndexed { index, _ -> index in selected }
            return withContext(Dispatchers.Default) {
                chosen.map { r ->
                    // App-reported bounds can reach past the capture (e.g. partly off screen).
                    val left = r.left.coerceIn(0, source.width - 1)
                    val top = r.top.coerceIn(0, source.height - 1)
                    val right = r.right.coerceIn(left + 1, source.width)
                    val bottom = r.bottom.coerceIn(top + 1, source.height)
                    Bitmap.createBitmap(source, left, top, right - left, bottom - top)
                }
            }
        }

        fun copy() {
            if (busy || selected.size != 1) return
            busy = true
            notice = null
            launchWork {
                val crop = crops().single()
                val copied = withContext(Dispatchers.IO) { ImageOutput.copy(service, crop) }
                if (crop !== image) crop.recycle()
                when {
                    !copied -> {
                        notice = service.getString(R.string.media_failed)
                        busy = false
                    }
                    // Android 13+ shows its own clipboard confirmation.
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> confirmAndClose(service.getString(R.string.media_copied))
                    else -> close()
                }
            }
        }

        fun save() {
            if (busy || selected.isEmpty()) return
            busy = true
            notice = null
            launchWork {
                val all = crops()
                val saved = withContext(Dispatchers.IO) { all.count { ImageOutput.save(service, it) } }
                all.forEach { if (it !== image) it.recycle() }
                if (saved == all.size) {
                    confirmAndClose(service.resources.getQuantityString(R.plurals.media_saved, saved, saved))
                } else {
                    notice = service.getString(R.string.media_failed)
                    busy = false
                }
            }
        }

        fun retake() {
            if (busy || retaking || !ScreenRecapture.isSupported) return
            retaking = true
            notice = null
            val current = image
            host.scope.launch {
                val fresh = ScreenRecapture.capture(service, current.width, current.height)
                when {
                    fresh == null -> notice = service.getString(R.string.media_capture_failed)
                    withContext(Dispatchers.Default) { ScreenImage.looksBlocked(fresh) } -> {
                        fresh.recycle()
                        notice = service.getString(R.string.media_blocked)
                    }
                    !window.isShowing -> fresh.recycle()
                    // The replaced image may still be referenced by a frame being rendered, so it is
                    // left to the garbage collector rather than recycled here.
                    else -> {
                        image = fresh
                        fromScan = null
                        selected = emptySet()
                        startScan()
                    }
                }
                retaking = false
            }
        }

        window = OverlayWindow(service, onDismissRequest = ::close)
        window.show {
            val bitmap = remember(image) { image.asImageBitmap() }
            PowerTheme(darkTheme = true) {
                MediaScreen(
                    image = bitmap,
                    view = view,
                    regions = if (view == MediaView.Accessibility) fromAccessibility else fromScan,
                    selected = selected,
                    busy = busy,
                    notice = notice,
                    onToggle = { index -> if (!busy) selected = if (index in selected) selected - index else selected + index },
                    onView = ::selectView,
                    onCopy = ::copy,
                    onSave = ::save,
                    onRetake = if (ScreenRecapture.isSupported) ::retake else null,
                    retaking = retaking,
                    onClose = ::close,
                )
            }
        }
        launchWork { fromAccessibility = MediaFinder.accessibility(service) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaExtractorBindings {
    @Binds
    @IntoSet
    fun bind(module: MediaExtractorModule): PanelModule
}
