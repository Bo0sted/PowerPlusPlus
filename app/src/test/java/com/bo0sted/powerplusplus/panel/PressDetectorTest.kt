package com.bo0sted.powerplusplus.panel

import com.bo0sted.powerplusplus.panel.PressDetector.Key
import com.bo0sted.powerplusplus.panel.PressDetector.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PressDetectorTest {

    @Test
    fun withoutADoublePressASinglePressIsImmediate() {
        val detector = PressDetector(windowMs = 300)
        assertEquals(listOf(Press(Key.Up, double = false)), detector.down(Key.Up, now = 1_000, hasDoublePress = false))
        assertNull(detector.deadline)
    }

    @Test
    fun twoPressesWithinTheWindowAreADoublePress() {
        val detector = PressDetector(windowMs = 300)
        assertEquals(emptyList<Press>(), detector.down(Key.Down, now = 1_000, hasDoublePress = true))
        assertEquals(1_300L, detector.deadline)
        assertEquals(listOf(Press(Key.Down, double = true)), detector.down(Key.Down, now = 1_300, hasDoublePress = true))
        assertNull(detector.deadline)
    }

    @Test
    fun aLonePressBecomesSingleWhenTheWindowEnds() {
        val detector = PressDetector(windowMs = 300)
        detector.down(Key.Up, now = 1_000, hasDoublePress = true)
        assertNull(detector.expire(now = 1_299))
        assertEquals(Press(Key.Up, double = false), detector.expire(now = 1_300))
        assertNull(detector.expire(now = 1_400))
    }

    @Test
    fun aLateSecondPressStartsAgain() {
        val detector = PressDetector(windowMs = 300)
        detector.down(Key.Up, now = 1_000, hasDoublePress = true)
        // The timer hadn't fired yet: the first press settles as single, the second one waits.
        assertEquals(listOf(Press(Key.Up, double = false)), detector.down(Key.Up, now = 1_301, hasDoublePress = true))
        assertEquals(1_601L, detector.deadline)
    }

    @Test
    fun theOtherKeySettlesTheWaitingOneFirst() {
        val detector = PressDetector(windowMs = 300)
        detector.down(Key.Up, now = 1_000, hasDoublePress = true)
        assertEquals(
            listOf(Press(Key.Up, double = false), Press(Key.Down, double = false)),
            detector.down(Key.Down, now = 1_100, hasDoublePress = false),
        )
        assertNull(detector.deadline)
    }
}
