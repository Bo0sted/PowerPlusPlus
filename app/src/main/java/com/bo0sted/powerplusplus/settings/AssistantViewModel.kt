package com.bo0sted.powerplusplus.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bo0sted.powerplusplus.assistant.AssistantCommand
import com.bo0sted.powerplusplus.assistant.AssistantCommands
import com.bo0sted.powerplusplus.download.ComponentState
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import com.bo0sted.powerplusplus.ntfy.NtfySettings
import com.bo0sted.powerplusplus.whisper.WhisperModel
import com.bo0sted.powerplusplus.whisper.WhisperModels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the Whisper assistant section: speech model, microphone, ntfy and commands. */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: WhisperModels,
    private val ntfyRepository: NtfyRepository,
    private val commands: AssistantCommands,
) : ViewModel() {

    val modelStates: StateFlow<Map<String, ComponentState>> = models.state
    val modelCatalog: List<WhisperModel> = models.components

    fun installModel(model: WhisperModel) = models.install(model)

    fun removeModel(model: WhisperModel) = models.remove(model)

    private val microphone = MutableStateFlow(false)
    val microphoneGranted: StateFlow<Boolean> = microphone.asStateFlow()

    /** Permissions change in system Settings too, so this is re-checked whenever the screen resumes. */
    fun refreshMicrophone() {
        microphone.value =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /** `null` until read. */
    val ntfy: StateFlow<NtfySettings?> = ntfyRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Test-and-save or re-check in progress, and the last failure; Compose state for the section. */
    var ntfyBusy by mutableStateOf(false)
        private set
    var ntfyError by mutableStateOf<String?>(null)
        private set

    fun setNtfyEnabled(enabled: Boolean) {
        viewModelScope.launch { ntfyRepository.setEnabled(enabled) }
    }

    /** Saves only if a test post succeeds (see [NtfyRepository.save]); otherwise shows why. */
    fun saveNtfy(url: String, topic: String, token: String) = ntfyCall { ntfyRepository.save(url, topic, token) }

    fun refreshNtfyLimit() = ntfyCall { ntfyRepository.refreshDelayLimit() }

    private fun ntfyCall(block: suspend () -> Unit) {
        if (ntfyBusy) return
        ntfyBusy = true
        ntfyError = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // NtfyException (the server's reason or a network error) or bad input.
                ntfyError = e.message ?: e.javaClass.simpleName
            } finally {
                ntfyBusy = false
            }
        }
    }

    val allCommands: List<AssistantCommand> = commands.all

    fun isOn(command: AssistantCommand): Flow<Boolean> = commands.isOn(command)

    fun setOn(command: AssistantCommand, on: Boolean) {
        viewModelScope.launch { commands.setOn(command, on) }
    }
}
