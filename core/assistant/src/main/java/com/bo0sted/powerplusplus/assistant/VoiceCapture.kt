package com.bo0sted.powerplusplus.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** Records from the microphone until one utterance is complete (see [Endpointer]) or the user stops it. */
internal object VoiceCapture {

    const val SAMPLE_RATE = 16_000

    /**
     * Records one utterance as 16 kHz mono samples in -1..1, reporting each frame's loudness (0..1)
     * to [onLevel] as it goes. Once [stop] completes, it returns at once with what was said so far
     * (everything recorded, if speech hadn't clearly started). The microphone is released when this
     * returns or is cancelled. The caller checks the RECORD_AUDIO permission first.
     */
    @SuppressLint("MissingPermission")
    suspend fun nextUtterance(stop: Deferred<Unit>, onLevel: (Float) -> Unit): FloatArray = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, Endpointer.FRAME_SAMPLES * 4 * 8),
        )
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IOException("The microphone isn't available")
            record.startRecording()
            val endpointer = Endpointer()
            val frame = FloatArray(Endpointer.FRAME_SAMPLES)
            val preRoll = ArrayDeque<FloatArray>()
            val speech = ArrayList<FloatArray>()
            // Everything since the start, for a stop before the endpointer heard speech (a quiet voice).
            val recorded = ArrayList<FloatArray>()
            var utterance: FloatArray? = null
            while (utterance == null) {
                coroutineContext.ensureActive()
                if (stop.isCompleted) {
                    utterance = concat(speech.ifEmpty { recorded })
                    continue // Ends the loop.
                }
                var filled = 0
                while (filled < frame.size) {
                    val read = record.read(frame, filled, frame.size - filled, AudioRecord.READ_BLOCKING)
                    if (read < 0) throw IOException("Microphone read failed ($read)")
                    filled += read
                }
                val rms = rms(frame)
                onLevel((sqrt(rms / LOUD_RMS)).coerceIn(0f, 1f))
                val copy = frame.copyOf()
                if (recorded.size < Endpointer.MAX_FRAMES) recorded.add(copy)
                when (endpointer.next(rms)) {
                    Endpointer.Event.Waiting -> {
                        preRoll.addLast(copy)
                        if (preRoll.size > Endpointer.PRE_ROLL_FRAMES) preRoll.removeFirst()
                    }
                    Endpointer.Event.Started -> {
                        speech.addAll(preRoll)
                        speech.add(copy)
                    }
                    Endpointer.Event.Speaking -> speech.add(copy)
                    Endpointer.Event.Ended -> {
                        speech.add(copy)
                        utterance = concat(speech)
                    }
                    Endpointer.Event.Discarded -> speech.clear()
                }
            }
            utterance
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
            onLevel(0f)
        }
    }

    private fun rms(frame: FloatArray): Float {
        var sum = 0.0
        for (sample in frame) sum += sample * sample
        return sqrt(sum / frame.size).toFloat()
    }

    private fun concat(frames: List<FloatArray>): FloatArray {
        val out = FloatArray(frames.sumOf { it.size })
        var offset = 0
        for (frame in frames) {
            frame.copyInto(out, offset)
            offset += frame.size
        }
        return out
    }

    /** RMS shown as full level; normal speech close to the phone. */
    private const val LOUD_RMS = 0.1f
}
