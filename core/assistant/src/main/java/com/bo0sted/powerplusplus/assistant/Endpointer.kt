package com.bo0sted.powerplusplus.assistant

import kotlin.math.max

/**
 * Finds one utterance in a stream of audio frames by loudness: speech starts after
 * [START_FRAMES] loud frames and ends after [END_FRAMES] quiet ones (or at [MAX_FRAMES]). "Loud" is
 * relative to a running estimate of the background noise, so it works in quiet rooms and on the
 * street. Pure; unit-tested.
 */
internal class Endpointer {
    private var noise = -1f
    private var loudRun = 0
    private var quietRun = 0
    private var speechFrames = 0
    private var framesSinceStart = -1 // -1 until speech starts.

    /** Feeds one frame's RMS; returns what happened. */
    fun next(rms: Float): Event {
        if (noise < 0) noise = rms
        val loud = rms > max(noise * NOISE_RATIO, MIN_SPEECH_RMS)
        if (!loud) noise += (rms - noise) * NOISE_ADAPTATION // Learn the background from quiet frames only.

        if (framesSinceStart < 0) {
            loudRun = if (loud) loudRun + 1 else 0
            if (loudRun < START_FRAMES) return Event.Waiting
            framesSinceStart = loudRun
            speechFrames = loudRun
            return Event.Started
        }
        framesSinceStart++
        if (loud) {
            speechFrames++
            quietRun = 0
        } else {
            quietRun++
        }
        return when {
            framesSinceStart >= MAX_FRAMES -> Event.Ended
            quietRun < END_FRAMES -> Event.Speaking
            speechFrames >= MIN_SPEECH_FRAMES -> Event.Ended
            else -> reset() // Too short to be speech (a knock, a click): keep waiting.
        }
    }

    private fun reset(): Event {
        loudRun = 0
        quietRun = 0
        speechFrames = 0
        framesSinceStart = -1
        return Event.Discarded
    }

    enum class Event { Waiting, Started, Speaking, Ended, Discarded }

    companion object {
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz.
        const val START_FRAMES = 3
        const val END_FRAMES = 40 // 0.8 s of quiet.
        const val MIN_SPEECH_FRAMES = 12 // 0.24 s.
        const val MAX_FRAMES = 1000 // 20 s.
        const val PRE_ROLL_FRAMES = 15 // 0.3 s kept from before speech started.
        private const val NOISE_RATIO = 2.5f
        private const val MIN_SPEECH_RMS = 0.004f
        private const val NOISE_ADAPTATION = 0.05f
    }
}
