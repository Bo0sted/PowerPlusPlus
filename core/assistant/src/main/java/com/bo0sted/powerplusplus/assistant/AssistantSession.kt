package com.bo0sted.powerplusplus.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import com.bo0sted.powerplusplus.whisper.SpeechDetector
import com.bo0sted.powerplusplus.whisper.SpeechModel
import com.bo0sted.powerplusplus.whisper.WhisperModels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Opens an [AssistantSession] each time the panel opens. */
@Singleton
class Assistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: WhisperModels,
    private val commands: AssistantCommands,
) {
    /** Starts listening (if a model is installed and a command is on); [AssistantSession.close] stops it. */
    fun open(scope: CoroutineScope): AssistantSession = AssistantSession(context, models, commands, scope)
}

/**
 * The assistant while the panel is open: it listens, transcribes each utterance with Whisper and
 * opens the command modal for the first command that recognises it. Everything runs on the device.
 * State is Compose state for [AssistantOverlay]; call from the main thread.
 *
 * Noise is kept out three ways: recording starts only when [SpeechDetector] hears speech, results
 * Whisper itself rates as non-speech are dropped, and transcripts that look like Whisper's guesses
 * for noise go unanswered instead of getting "Command not recognized" ([Transcripts]).
 */
class AssistantSession internal constructor(
    private val context: Context,
    private val models: WhisperModels,
    private val commands: AssistantCommands,
    private val scope: CoroutineScope,
) {
    sealed interface Status {
        /** No model installed or no command on: the assistant isn't shown at all. */
        data object Off : Status
        data object NeedsMicrophone : Status
        data object Listening : Status
        data object Transcribing : Status
        data class Failed(val reason: String) : Status
    }

    var status by mutableStateOf<Status>(Status.Off)
        private set

    /** Microphone loudness, 0..1, while listening. */
    var level by mutableFloatStateOf(0f)
        private set

    /** A short message above the voice bar (e.g. "Command not recognized"), with what was heard. */
    var notice by mutableStateOf<Notice?>(null)
        private set

    /** The command being reviewed in the modal. Listening pauses while it's open. */
    val modal = CommandModalState()

    /** True while a text field in the modal has focus; the window then makes room for the keyboard. */
    var editingText by mutableStateOf(false)
        internal set

    /** Incremented to ask the modal to drop text focus (Back while typing). */
    internal var clearFocusRequests by mutableIntStateOf(0)
        private set

    data class Notice(@StringRes val message: Int, val heard: String?)

    private var model: Deferred<SpeechModel>? = null
    private var detector: SpeechDetector? = null
    private var stopListening: CompletableDeferred<Unit>? = null
    private var current: Job? = null
    private var noticeJob: Job? = null
    private val job: Job = scope.launch { run() } // Last: run() starts right away.

    private suspend fun run() {
        val active = commands.active.first()
        val installed = models.installed()
        if (active.isEmpty() || installed == null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = Status.NeedsMicrophone
            return
        }
        // Loads (about a second) while the user is still speaking.
        val loading = scope.async(Dispatchers.Default) { SpeechModel.load(context, models.modelFile(installed), installed.language) }
        model = loading
        try {
            // Needed from the first window of audio, and small, so it's loaded before listening.
            val speechDetector = withContext(Dispatchers.Default) { SpeechDetector.load(context, models.speechDetectorFile(installed)) }
            detector = speechDetector
            while (true) {
                // Paused while a modal is open (a spoken command's, or one opened from a module).
                snapshotFlow { modal.draft }.first { it == null }
                val stop = CompletableDeferred<Unit>()
                stopListening = stop
                // Its own job, so a tap can drop it (see onWaveTap) without ending the session.
                val utterance = scope.async {
                    status = Status.Listening
                    val audio = VoiceCapture.nextUtterance(stop, speechDetector) { level = it }
                    status = Status.Transcribing
                    // A dropped transcription still finishes in whisper.cpp; its result is ignored.
                    withContext(Dispatchers.Default) { loading.await().transcribe(audio) }
                }
                current = utterance
                val transcript = try {
                    utterance.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive() // The session closed: stop.
                    continue // Dropped by a tap: start fresh.
                }
                val text = transcript.text
                Log.i(TAG, "Heard: $text (no speech: ${"%.2f".format(Locale.ROOT, transcript.noSpeechProbability)})")
                if (Transcripts.verdict(transcript) == Transcripts.Verdict.Noise) continue
                val now = ZonedDateTime.now()
                val match = commands.active.first().firstNotNullOfOrNull { it.draftFor(text, now) }
                if (match == null) {
                    if (Transcripts.isUnanswered(text)) Log.i(TAG, "Ignored as noise") else show(Notice(R.string.assistant_not_recognized, text))
                    continue
                }
                notice = null
                editingText = false
                modal.open(match)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Assistant stopped", e)
            status = Status.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            current?.cancel()
        }
    }

    /** Opens [draft] in the modal (from a [CommandModule]); anything being heard is dropped. */
    fun open(draft: CommandDraft) {
        current?.cancel()
        notice = null
        editingText = false
        modal.open(draft)
    }

    /**
     * The voice wave was tapped. While listening: stop the microphone now and transcribe what was
     * said. While transcribing: drop it and everything said, and listen afresh.
     */
    fun onWaveTap() {
        when (status) {
            Status.Listening -> stopListening?.complete(Unit)
            Status.Transcribing -> current?.cancel()
            else -> Unit
        }
    }

    private fun show(message: Notice) {
        notice = message
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(NOTICE_MS)
            notice = null
        }
    }

    fun confirmDraft() = modal.confirm(scope) { editingText = false }

    /** Back: leaves text editing, then steps back within the modal, then closes it. False if there's no modal. */
    fun onBack(): Boolean {
        if (modal.draft == null) return false
        if (editingText) clearFocusRequests++ else modal.onBack()
        return true
    }

    /** Stops listening and frees the models once any recording or transcription in progress has finished. */
    fun close() {
        noticeJob?.cancel()
        scope.launch {
            job.cancel()
            job.join()
            // The recording (which uses the detector) and transcription stop at their next check.
            current?.join()
            detector?.close()
            // Loading can't be interrupted (it's native code), so wait for it and then free the model.
            runCatching { model?.await() }.getOrNull()?.close()
        }
    }

    private companion object {
        const val TAG = "PowerPP.Assistant"
        const val NOTICE_MS = 2_500L
    }
}
