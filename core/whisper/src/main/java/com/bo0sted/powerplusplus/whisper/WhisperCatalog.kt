package com.bo0sted.powerplusplus.whisper

import com.bo0sted.powerplusplus.download.Downloadable
import com.bo0sted.powerplusplus.download.RemoteFile

/**
 * Speech models, pinned to exact files from their official publishers. Verified 2026-10-12 (the
 * speech detector 2026-10-24); see AGENTS.md §3.8.
 */
object WhisperCatalog {

    /**
     * FUTO's ACFT fine-tune of Whisper base.en, quantised to q8_0 (GGML), from FUTO's own download
     * host. Its SHA-256 is the one FUTO Keyboard pins for `base_en_acft_q8_0.bin`. ACFT models stay
     * accurate with a short audio context, which makes short commands several times faster.
     *
     * Bundled with Silero VAD v6.2.0 (MIT), the speech detector that decides when someone is
     * talking, converted for whisper.cpp by ggml-org and pinned to its Hugging Face commit. One
     * download, so the assistant never has one without the other.
     */
    val BASE_EN = WhisperModel(
        id = "model-whisper-base-en-acft",
        // Changed when the files change: an install of another version is outdated (ComponentStore).
        version = "base.en + Silero VAD 6.2.0",
        language = "en",
        files = listOf(
            RemoteFile(
                name = MODEL_FILE,
                sizeBytes = 81_781_811,
                sha256 = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8",
                urls = listOf(
                    "https://keyboard.futo.org/voice-input-english-74.bin",
                    "https://dl.keyboard.futo.org/voice-input-english-74.bin",
                ),
            ),
            RemoteFile(
                name = SPEECH_DETECTOR_FILE,
                sizeBytes = 885_098,
                sha256 = "2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987",
                urls = listOf(
                    "https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/ggml-silero-v6.2.0.bin",
                ),
            ),
        ),
    )

    val ALL = listOf(BASE_EN)

    const val MODEL_FILE = "model.bin"
    const val SPEECH_DETECTOR_FILE = "vad.bin"
}

data class WhisperModel(
    override val id: String,
    override val version: String,
    /** Whisper's language code; English-only models need "en". */
    val language: String,
    override val files: List<RemoteFile>,
) : Downloadable
