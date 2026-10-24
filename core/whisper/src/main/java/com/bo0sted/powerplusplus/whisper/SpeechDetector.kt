package com.bo0sted.powerplusplus.whisper

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Silero VAD, run by whisper.cpp: how likely a short window of audio is to be speech, as opposed to
 * noise (a fan, a cough, music). It's a recurrent network, so it keeps state from one window to the
 * next: feed windows in order and [reset] before an unrelated stretch of audio. The model is small
 * (885 KB). Calls are serialised; run them off the main thread.
 */
class SpeechDetector private constructor(private var handle: Long) : Closeable {

    /** The probability (0..1) that [window], [WINDOW_SAMPLES] samples at 16 kHz in -1..1, is speech. */
    @Synchronized
    fun speechProbability(window: FloatArray): Float {
        check(handle != 0L) { "closed" }
        val probability = WhisperNative.vadProbability(handle, window)
        if (probability < 0f) throw IOException("whisper.cpp couldn't run the speech detector")
        return probability
    }

    /** Forgets the audio heard so far. */
    @Synchronized
    fun reset() {
        check(handle != 0L) { "closed" }
        WhisperNative.vadReset(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) WhisperNative.vadFree(handle)
        handle = 0L
    }

    companion object {
        /** Silero's window at 16 kHz: 32 ms. */
        const val WINDOW_SAMPLES = 512

        /** Throws [IOException] if the file can't be loaded or this device has no speech engine. */
        fun load(context: Context, file: File): SpeechDetector {
            WhisperNative.ensureLoaded(context)
            val handle = WhisperNative.vadLoad(file.absolutePath)
            if (handle == 0L) throw IOException("whisper.cpp couldn't load ${file.name}")
            return SpeechDetector(handle)
        }
    }
}
