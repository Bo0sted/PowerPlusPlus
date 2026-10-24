package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.whisper.Transcript

/**
 * What to do with a transcript, after the speech detector has already kept most noise out. Whisper
 * writes something for any audio, so its guesses for noise ("I", "you", "Thank you.") are dropped
 * silently instead of answered with "Command not recognized". Pure; unit-tested.
 */
internal object Transcripts {

    enum class Verdict {
        /** Probably not speech: ignore it without a word. */
        Noise,

        /** Speech: try the commands, and say so if none matches. */
        Speech,
    }

    /**
     * Whisper's own no-speech probability above this means the text is made up. 0.6 is whisper.cpp's
     * default `no_speech_thold`.
     */
    const val MAX_NO_SPEECH_PROBABILITY = 0.6f

    /** Whether [transcript] is worth matching against commands at all. */
    fun verdict(transcript: Transcript): Verdict =
        if (transcript.noSpeechProbability > MAX_NO_SPEECH_PROBABILITY || words(transcript.text).isEmpty()) Verdict.Noise else Verdict.Speech

    /**
     * Whether [text], which matched no command, should go unanswered: a single word (every command
     * needs more, e.g. "remind me …") or one of Whisper's usual phrases for silence and noise.
     */
    fun isUnanswered(text: String): Boolean {
        val words = words(text)
        return words.size < 2 || words.joinToString(" ") in NOISE_PHRASES
    }

    /** Lowercase words, without punctuation or Whisper's bracketed sound labels ("[BLANK_AUDIO]", "(music)"). */
    internal fun words(text: String): List<String> =
        text.lowercase()
            .replace(SOUND_LABEL, " ")
            .replace(NOT_WORD, " ")
            .split(' ')
            .filter { it.isNotEmpty() }

    private val SOUND_LABEL = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|♪+""")
    private val NOT_WORD = Regex("""[^\p{L}\p{N}']+""")

    /** Phrases Whisper is known to write for silence or noise (FUTO Keyboard filters similar ones). */
    private val NOISE_PHRASES = setOf(
        "thank you",
        "thank you so much",
        "thank you very much",
        "thanks for watching",
        "thank you for watching",
        "thanks for listening",
        "please subscribe",
        "bye bye",
        "blank audio",
        "music playing",
    )
}
