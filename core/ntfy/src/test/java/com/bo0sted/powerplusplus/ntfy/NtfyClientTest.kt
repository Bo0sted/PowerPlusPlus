package com.bo0sted.powerplusplus.ntfy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyClientTest {

    private fun limitFor(serverLimitSeconds: Long): Pair<Long?, Int> {
        var probes = 0
        val found = runBlocking {
            NtfyClient.findDelayLimit { seconds -> probes++; seconds <= serverLimitSeconds }
        }
        return found to probes
    }

    @Test
    fun findsNtfyShsThreeDayDefaultExactly() {
        val (limit, probes) = limitFor(3 * 24 * 3600L)
        assertEquals(3 * 24 * 3600L, limit)
        assertTrue("$probes probes", probes <= 24)
    }

    @Test
    fun roundsDownToWholeMinutes() {
        assertEquals(60L * 90, limitFor(90 * 60 + 59).first)
    }

    @Test
    fun capsAtTheProbedMaximum() {
        assertEquals(NtfyClient.MAX_PROBED_SECONDS, limitFor(Long.MAX_VALUE).first)
    }

    @Test
    fun noneWhenEvenAMinuteIsRefused() {
        assertNull(limitFor(30).first)
    }

    @Test
    fun serverAddressesAreNormalisedAndHttpsOnly() {
        assertEquals("https://ntfy.sh", NtfyRepository.normalizeUrl(" ntfy.sh/ "))
        assertEquals("https://example.com:8443/ntfy", NtfyRepository.normalizeUrl("https://example.com:8443/ntfy/"))
        assertThrows(IllegalArgumentException::class.java) { NtfyRepository.normalizeUrl("http://ntfy.sh") }
        assertThrows(IllegalArgumentException::class.java) { NtfyRepository.normalizeUrl("") }
    }

    @Test
    fun topicsFollowTheServersRule() {
        assertTrue(NtfyRepository.TOPIC.matches("power_plus-plus-123"))
        assertTrue(!NtfyRepository.TOPIC.matches("has space") && !NtfyRepository.TOPIC.matches("a/b") && !NtfyRepository.TOPIC.matches(""))
    }
}
