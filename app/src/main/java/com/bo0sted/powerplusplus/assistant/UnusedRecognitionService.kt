package com.bo0sted.powerplusplus.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Exists only because `voice-interaction-service` metadata must name a recognition service; the
 * platform ignores it since Android 12 (our minSdk). Refuses any request that somehow reaches it.
 */
class UnusedRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
