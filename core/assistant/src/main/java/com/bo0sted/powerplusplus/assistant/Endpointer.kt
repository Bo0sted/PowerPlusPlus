package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.whisper.SpeechDetector

/**
 * Finds one utterance in a stream of audio windows from each window's speech probability (Silero
 * VAD, [SpeechDetector]), not its loudness, so noise doesn't count
 * however loud it is. Speech starts after [START_FRAMES] windows in a row at or above
 * [SPEECH_THRESHOLD] and ends after [END_FRAMES] windows in a row below [SILENCE_THRESHOLD] (or at
 * [MAX_FRAMES]). In between the two thresholds a window neither starts nor ends speech, as in
 * Silero's own `get_speech_timestamps`, so a dip in the middle of a word doesn't end it. Needing
 * [START_FRAMES] of speech first is also what keeps out knocks and clicks. Pure; unit-tested.
 */
internal class Endpointer {
    private var speechRun = 0
    private var silenceRun = 0
    private var framesSinceStart = -1 // -1 until speech starts.

    /** Feeds one window's speech probability; returns what happened. */
    fun next(probability: Float): Event {
        val speech = probability >= SPEECH_THRESHOLD
        val silence = probability < SILENCE_THRESHOLD

        if (framesSinceStart < 0) {
            speechRun = if (speech) speechRun + 1 else 0
            if (speechRun < START_FRAMES) return Event.Waiting
            framesSinceStart = speechRun
            return Event.Started
        }
        framesSinceStart++
        silenceRun = if (silence) silenceRun + 1 else 0
        return if (framesSinceStart >= MAX_FRAMES || silenceRun >= END_FRAMES) Event.Ended else Event.Speaking
    }

    enum class Event { Waiting, Started, Speaking, Ended }

    companion object {
        /** Silero's window and its defaults: speech from 0.5, silence below 0.5 − 0.15. */
        const val FRAME_SAMPLES = SpeechDetector.WINDOW_SAMPLES // 32 ms at 16 kHz.
        const val SPEECH_THRESHOLD = 0.5f
        const val SILENCE_THRESHOLD = 0.35f
        const val START_FRAMES = 8 // 0.26 s of speech before it counts.
        const val END_FRAMES = 25 // 0.8 s of silence.
        const val MAX_FRAMES = 625 // 20 s.

        /** Kept from before speech started: the [START_FRAMES] it takes to be sure, plus 0.32 s. */
        const val PRE_ROLL_FRAMES = START_FRAMES + 10
    }
}
