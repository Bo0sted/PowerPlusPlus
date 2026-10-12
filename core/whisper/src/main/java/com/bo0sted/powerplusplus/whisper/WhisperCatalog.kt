package com.bo0sted.powerplusplus.whisper

import com.bo0sted.powerplusplus.download.Downloadable
import com.bo0sted.powerplusplus.download.RemoteFile

/**
 * Speech models, pinned to exact files from their official publisher. Verified 2026-10-12; see
 * AGENTS.md §3.8.
 */
object WhisperCatalog {

    /**
     * FUTO's ACFT fine-tune of Whisper base.en, quantised to q8_0 (GGML), from FUTO's own download
     * host. Its SHA-256 is the one FUTO Keyboard pins for `base_en_acft_q8_0.bin`. ACFT models stay
     * accurate with a short audio context, which makes short commands several times faster.
     */
    val BASE_EN = WhisperModel(
        id = "model-whisper-base-en-acft",
        version = "base.en",
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
        ),
    )

    val ALL = listOf(BASE_EN)

    const val MODEL_FILE = "model.bin"
}

data class WhisperModel(
    override val id: String,
    override val version: String,
    /** Whisper's language code; English-only models need "en". */
    val language: String,
    override val files: List<RemoteFile>,
) : Downloadable
