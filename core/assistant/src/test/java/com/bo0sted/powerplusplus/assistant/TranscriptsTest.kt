package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.assistant.Transcripts.Verdict
import com.bo0sted.powerplusplus.whisper.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptsTest {

    @Test
    fun whisperSayingThereWasNoSpeechIsNoise() {
        assertEquals(Verdict.Noise, Transcripts.verdict(Transcript("Remind me to call Mom.", noSpeechProbability = 0.8f)))
        assertEquals(Verdict.Speech, Transcripts.verdict(Transcript("Remind me to call Mom.", noSpeechProbability = 0.1f)))
    }

    @Test
    fun soundLabelsAndPunctuationAloneAreNoise() {
        listOf("", "[BLANK_AUDIO]", "(music)", "♪♪", "...", "*coughs*").forEach {
            assertEquals(it, Verdict.Noise, Transcripts.verdict(Transcript(it, noSpeechProbability = 0f)))
        }
    }

    @Test
    fun singleWordsAndKnownNoisePhrasesGoUnanswered() {
        listOf("I", "you.", "Thank you.", "Thanks for watching!", "(sigh) Bye bye.").forEach {
            assertTrue(it, Transcripts.isUnanswered(it))
        }
        listOf("What's the weather?", "Turn off the lights", "Thank you, call Sam").forEach {
            assertFalse(it, Transcripts.isUnanswered(it))
        }
    }

    @Test
    fun wordsKeepApostrophesAndDropLabels() {
        assertEquals(listOf("don't", "stop", "now"), Transcripts.words("Don't [noise] stop, now!"))
    }
}
