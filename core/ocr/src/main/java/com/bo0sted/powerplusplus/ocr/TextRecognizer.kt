package com.bo0sted.powerplusplus.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs OCR with the installed PP-OCR model. The models load on first use (slow) and stay loaded for
 * the life of the process; calls run one at a time on [Dispatchers.Default].
 */
@Singleton
class TextRecognizer @Inject constructor(private val components: OcrComponents) {
    private val mutex = Mutex()
    private var ocr: PpOcr? = null

    /** The engine and model are both installed, at the versions this build uses. */
    val isInstalled: Boolean
        get() = components.isInstalled(OcrCatalog.ENGINE) && components.isInstalled(OcrCatalog.PP_OCR_V6_MEDIUM)

    /** Loads the models ahead of [recognize], e.g. while the user is still choosing a region. */
    suspend fun preload() {
        withContext(Dispatchers.Default) { mutex.withLock { loaded() } }
    }

    /** The lines of text in [image], a software bitmap. Requires [isInstalled]. */
    suspend fun recognize(image: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        mutex.withLock { loaded().recognize(image) }
    }

    private fun loaded(): PpOcr = ocr ?: run {
        val started = SystemClock.elapsedRealtime()
        PpOcr.open(components.engineLibrary(), components.directory(OcrCatalog.PP_OCR_V6_MEDIUM)).also {
            Log.d(TAG, "Models loaded in ${SystemClock.elapsedRealtime() - started} ms")
            ocr = it
        }
    }

    private companion object {
        const val TAG = "PowerPP.Ocr"
    }
}
