package com.bo0sted.powerplusplus.module.ocrtext

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import com.bo0sted.powerplusplus.capture.ScreenImage
import com.bo0sted.powerplusplus.capture.ScreenRecapture
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import com.bo0sted.powerplusplus.ocr.TextRecognizer
import com.bo0sted.powerplusplus.overlay.OverlayWindow
import com.bo0sted.powerplusplus.textpick.TextBlock
import com.bo0sted.powerplusplus.textpick.TextPickScreen
import com.bo0sted.powerplusplus.textpick.TextPickState
import com.bo0sted.powerplusplus.textpick.copyToClipboard
import com.bo0sted.powerplusplus.textpick.normalizeTextBlocks
import com.bo0sted.powerplusplus.ui.PowerTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Recognises text in a region of the screen as it was when Power++ was activated: the user adjusts
 * Smart screenshot's resizable selection, confirms, and picks from the recognised lines. Problems are
 * shown in the window itself rather than as toasts, which Android hides while notifications are off.
 */
class OcrTextModule @Inject constructor(private val recognizer: TextRecognizer) : ActionModule {
    override val typeId = "ocr_text"
    override val title = R.string.module_ocr_text
    override val icon = Icons.Rounded.DocumentScanner
    override val usesActivationScreenshot = true

    override suspend fun run(host: ModuleHost) {
        val captured = host.activationScreenshot.take()
        val image = captured?.let { withContext(Dispatchers.Default) { ScreenImage.toSoftware(it).also { captured.recycle() } } }
        val stage = when {
            image == null -> Stage.Problem(R.string.ocr_capture_failed)
            withContext(Dispatchers.Default) { ScreenImage.looksBlocked(image) } -> Stage.Problem(R.string.ocr_blocked)
            !recognizer.isInstalled -> Stage.Problem(R.string.ocr_not_installed, ProblemAction.OpenSettings)
            else -> Stage.Select()
        }
        show(host, image, stage)
        // Loads the models (about a second) while the user is still choosing a region.
        if (stage is Stage.Select) host.scope.launch { runCatching { recognizer.preload() }.onFailure { Log.e(TAG, "Preload failed", it) } }
    }

    private fun show(host: ModuleHost, initialImage: Bitmap?, initialStage: Stage) {
        val service = host.service
        lateinit var window: OverlayWindow
        var stage by mutableStateOf(initialStage)
        // Replaced by Retake; the frozen activation capture is the first value.
        var image by mutableStateOf(initialImage)
        var retaking by mutableStateOf(false)
        var recognition: Job? = null

        fun close() {
            if (!window.isShowing) return
            window.dismiss()
            // Recognition may still be reading the image; recycle it once that's over.
            val last = image
            val running = recognition
            host.scope.launch {
                running?.join()
                last?.recycle()
            }
        }

        fun confirm(selection: Rect) {
            val source = image ?: return
            val region = selection.toRegion(source.width, source.height)
            stage = Stage.Recognizing
            recognition = host.scope.launch {
                stage = try {
                    val blocks = recognize(source, region)
                    if (blocks.isEmpty()) Stage.Problem(R.string.ocr_no_text, ProblemAction.TryAgain) else Stage.Results(TextPickState(blocks))
                } catch (e: Exception) {
                    Log.e(TAG, "OCR failed", e)
                    Stage.Problem(R.string.ocr_failed, ProblemAction.TryAgain)
                }
            }
        }

        fun retake() {
            val current = image ?: return
            if (retaking || !ScreenRecapture.isSupported) return
            retaking = true
            host.scope.launch {
                val fresh = ScreenRecapture.capture(service, current.width, current.height)
                when {
                    fresh == null -> stage = Stage.Select(notice = R.string.ocr_capture_failed)
                    withContext(Dispatchers.Default) { ScreenImage.looksBlocked(fresh) } -> {
                        fresh.recycle()
                        stage = Stage.Select(notice = R.string.ocr_blocked)
                    }
                    !window.isShowing -> fresh.recycle()
                    // The replaced image may still be referenced by a frame being rendered, so it is
                    // left to the garbage collector rather than recycled here.
                    else -> {
                        image = fresh
                        stage = Stage.Select()
                    }
                }
                retaking = false
            }
        }

        fun onProblemAction(action: ProblemAction) {
            when (action) {
                ProblemAction.TryAgain -> stage = Stage.Select()
                ProblemAction.OpenSettings -> {
                    // Started while our overlay is still visible, which is what allows the activity start.
                    service.packageManager.getLaunchIntentForPackage(service.packageName)
                        ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    close()
                }
            }
        }

        window = OverlayWindow(
            service,
            onDismissRequest = ::close,
            // Back leaves editing first, so a long OCR result isn't lost.
            onBackRequest = { (stage as? Stage.Results)?.state?.takeIf { it.editing != null }?.cancelEdit() ?: close() },
        )
        window.show {
            val bitmap = remember(image) { image?.asImageBitmap() }
            PowerTheme(darkTheme = true) {
                when (val current = stage) {
                    is Stage.Results -> TextPickScreen(
                        state = current.state,
                        onCopy = { text ->
                            copyToClipboard(service, text)
                            close()
                        },
                        onClose = ::close,
                        onEditingChange = window::setCompact,
                        background = bitmap,
                        closeOnTapOutside = false,
                    )
                    else -> RegionScreen(
                        stage = current,
                        image = bitmap,
                        onConfirm = ::confirm,
                        onRetake = if (ScreenRecapture.isSupported) ::retake else null,
                        retaking = retaking,
                        onProblemAction = ::onProblemAction,
                        onClose = ::close,
                    )
                }
            }
        }
    }

    /** The text in [region] of [image], as blocks in screen pixels. */
    private suspend fun recognize(image: Bitmap, region: IntRect): List<TextBlock> {
        val crop = withContext(Dispatchers.Default) { Bitmap.createBitmap(image, region.left, region.top, region.width, region.height) }
        val lines = try {
            recognizer.recognize(crop)
        } finally {
            // createBitmap returns the image itself when the region covers all of it.
            if (crop !== image) crop.recycle()
        }
        return normalizeTextBlocks(
            lines.map { line ->
                val box = line.box
                TextBlock(line.text, IntRect(region.left + box.left, region.top + box.top, region.left + box.right, region.top + box.bottom))
            },
        )
    }

    private companion object {
        const val TAG = "PowerPP.Ocr"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface OcrTextBindings {
    @Binds
    @IntoSet
    fun bind(module: OcrTextModule): PanelModule
}
