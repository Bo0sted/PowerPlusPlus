package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.assistant.Endpointer.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointerTest {

    private fun run(vararg segments: Pair<Float, Int>): List<Event> {
        val endpointer = Endpointer()
        return segments.flatMap { (rms, frames) -> List(frames) { endpointer.next(rms) } }
    }

    @Test
    fun speechBetweenQuietIsOneUtterance() {
        val events = run(0.002f to 50, 0.05f to 60, 0.002f to Endpointer.END_FRAMES)
        assertEquals(Event.Started, events[50 + Endpointer.START_FRAMES - 1])
        assertEquals(Event.Ended, events.last())
        assertEquals(1, events.count { it == Event.Ended })
    }

    @Test
    fun shortPausesDontEndIt() {
        val events = run(0.002f to 50, 0.05f to 40, 0.002f to 20, 0.05f to 40, 0.002f to 10)
        assertEquals(0, events.count { it == Event.Ended })
    }

    @Test
    fun clicksAreDiscarded() {
        val events = run(0.002f to 50, 0.05f to 5, 0.002f to Endpointer.END_FRAMES)
        assertEquals(Event.Discarded, events.last())
    }

    @Test
    fun steadyNoiseIsNotSpeech() {
        val events = run(0.02f to 500)
        assertEquals(0, events.count { it == Event.Started })
    }

    @Test
    fun endlessSpeechIsCutAtTheMaximum() {
        val events = run(0.002f to 10, 0.05f to Endpointer.MAX_FRAMES + 10)
        assertEquals(Event.Ended, events[10 + Endpointer.MAX_FRAMES - 1])
    }
}
