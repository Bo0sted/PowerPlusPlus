package com.bo0sted.powerplusplus.whisper

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.min

/**
 * A loaded whisper.cpp model. Loading takes up to a second and the model holds a few hundred MB, so
 * load it only while it's needed. Calls are serialised; run them off the main thread.
 */
class SpeechModel private constructor(private var handle: Long, private val language: String) : Closeable {

    /** What was said in [samples] (16 kHz mono, -1..1). */
    @Synchronized
    fun transcribe(samples: FloatArray): Transcript {
        check(handle != 0L) { "closed" }
        val noSpeech = FloatArray(1)
        val bytes = WhisperNative.transcribe(handle, samples, language, THREADS, audioContext(samples.size), noSpeech)
            ?: throw IOException("whisper.cpp couldn't transcribe the audio")
        return Transcript(bytes.toString(Charsets.UTF_8).trim(), noSpeech[0])
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) WhisperNative.free(handle)
        handle = 0L
    }

    companion object {
        private val THREADS = min(4, Runtime.getRuntime().availableProcessors())

        /** Throws [IOException] if the file can't be loaded or this device has no speech engine. */
        fun load(context: Context, file: File, language: String): SpeechModel {
            WhisperNative.ensureLoaded(context)
            val handle = WhisperNative.load(file.absolutePath)
            if (handle == 0L) throw IOException("whisper.cpp couldn't load ${file.name}")
            return SpeechModel(handle, language)
        }

        /**
         * The encoder context for [sampleCount] samples. Whisper's encoder covers 30 s with 1500
         * positions (one per 320 samples at 16 kHz) and normally always runs all of them; ACFT models
         * stay accurate when it covers only the audio plus a margin, which is far faster for short
         * commands. The 32-position margin and 160 minimum are what FUTO Keyboard uses with them.
         */
        internal fun audioContext(sampleCount: Int): Int =
            (ceil(sampleCount / 320.0).toInt() + 32).coerceIn(160, 1500)
    }
}

/**
 * What Whisper made of some audio: [text], trimmed (empty if nothing was recognised), and
 * [noSpeechProbability], Whisper's own estimate (0..1) that there was no speech at all. Whisper
 * always produces some text, even for noise ("I", "you"), so a high value means the text is made up.
 */
data class Transcript(val text: String, val noSpeechProbability: Float)

/** The JNI functions in `powerpp_whisper.cpp`. */
internal object WhisperNative {
    @Volatile private var loaded = false

    /** Loads the native libraries and ggml's best CPU backend for this device, once. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        try {
            System.loadLibrary("powerpp_whisper")
        } catch (e: UnsatisfiedLinkError) {
            throw IOException("Speech recognition isn't available on this device's CPU", e)
        }
        // The CPU backends are separate libraries, found by listing this directory (the app's
        // packaging extracts native libraries for that).
        loadBackends(context.applicationInfo.nativeLibraryDir)
        loaded = true
    }

    external fun loadBackends(libraryDir: String)
    external fun load(modelPath: String): Long
    /** [noSpeechOut] receives the no-speech probability (one element). */
    external fun transcribe(handle: Long, samples: FloatArray, language: String, threads: Int, audioContext: Int, noSpeechOut: FloatArray): ByteArray?
    external fun free(handle: Long)

    external fun vadLoad(modelPath: String): Long
    external fun vadProbability(handle: Long, samples: FloatArray): Float
    external fun vadReset(handle: Long)
    external fun vadFree(handle: Long)
}
