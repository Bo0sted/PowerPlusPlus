package com.bo0sted.powerplusplus.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelTest {

    @Test
    fun audioContextCoversTheAudioPlusAMargin() {
        assertEquals(160, SpeechModel.audioContext(0)) // Minimum.
        assertEquals(50 * 5 + 32, SpeechModel.audioContext(16_000 * 5)) // 5 s: 50 positions per second.
        assertEquals(1500, SpeechModel.audioContext(16_000 * 40)) // Never past Whisper's 30 s window.
    }

    @Test
    fun everyModelIsPinnedAndServedOverHttps() {
        WhisperCatalog.ALL.flatMap { it.files }.forEach { file ->
            assertTrue(file.name, Regex("[0-9a-f]{64}").matches(file.sha256))
            assertTrue(file.name, file.sizeBytes > 0 && file.urls.all { it.startsWith("https://") })
        }
    }
}
