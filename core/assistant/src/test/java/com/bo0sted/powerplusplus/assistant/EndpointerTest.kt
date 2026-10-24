package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.assistant.Endpointer.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointerTest {

    private fun run(vararg segments: Pair<Float, Int>): List<Event> {
        val endpointer = Endpointer()
        return segments.flatMap { (probability, frames) -> List(frames) { endpointer.next(probability) } }
    }

    @Test
    fun speechBetweenSilenceIsOneUtterance() {
        val events = run(0.02f to 50, 0.9f to 40, 0.02f to Endpointer.END_FRAMES)
        assertEquals(Event.Started, events[50 + Endpointer.START_FRAMES - 1])
        assertEquals(Event.Ended, events.last())
        assertEquals(1, events.count { it == Event.Ended })
    }

    @Test
    fun shortPausesAndDipsDontEndIt() {
        // A 0.5 s pause, then a long stretch between the two thresholds (neither speech nor silence).
        val events = run(0.02f to 50, 0.9f to 30, 0.02f to 15, 0.9f to 30, 0.4f to 100)
        assertEquals(0, events.count { it == Event.Ended })
    }

    @Test
    fun noiseThatIsNotSpeechNeverStarts() {
        // However loud: the detector scores a fan or music low, now and then crossing the threshold briefly.
        val noise = List(500) { if (it % 10 < 3) 0.7f else 0.2f }
        val endpointer = Endpointer()
        assertEquals(0, noise.map(endpointer::next).count { it == Event.Started })
    }

    @Test
    fun aBurstShorterThanTheStartNeverStarts() {
        val events = run(0.02f to 50, 0.9f to Endpointer.START_FRAMES - 1, 0.02f to 50)
        assertEquals(listOf(Event.Waiting), events.distinct())
    }

    @Test
    fun endlessSpeechIsCutAtTheMaximum() {
        val events = run(0.02f to 10, 0.9f to Endpointer.MAX_FRAMES + 10)
        assertEquals(Event.Ended, events[10 + Endpointer.MAX_FRAMES - 1])
    }
}
