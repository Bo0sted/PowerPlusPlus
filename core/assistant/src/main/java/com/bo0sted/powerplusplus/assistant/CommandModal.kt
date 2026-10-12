package com.bo0sted.powerplusplus.assistant

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The [CommandDraft] shown in a [CommandModal], if any. Confirm runs it; a failure keeps it open with
 * the reason. Shared by the panel's assistant and any screen that edits a command's results (e.g.
 * upcoming reminders). Compose state; main thread.
 */
class CommandModalState {
    var draft by mutableStateOf<CommandDraft?>(null)
        private set
    var confirming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun open(draft: CommandDraft) {
        error = null
        this.draft = draft
    }

    fun cancel() {
        if (!confirming) draft = null
    }

    /** Runs the draft in [scope]; closes the modal and calls [onDone] only if it succeeds. */
    fun confirm(scope: CoroutineScope, onDone: () -> Unit = {}) {
        val current = draft ?: return
        if (confirming) return
        confirming = true
        error = null
        scope.launch {
            try {
                current.confirm()
                if (draft === current) draft = null
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Command failed", e)
                error = e.message ?: e.javaClass.simpleName
            } finally {
                confirming = false
            }
        }
    }

    /** Back: steps back within the draft, else closes the modal. False if no modal is open. */
    fun onBack(): Boolean {
        val current = draft ?: return false
        if (!current.onBack()) cancel()
        return true
    }

    private companion object {
        const val TAG = "PowerPP.Assistant"
    }
}

/**
 * The shared modal card for a command: its title, the draft's settings, why it can't be confirmed
 * yet, then Cancel and Confirm. The caller places and sizes it.
 */
@Composable
fun CommandModal(state: CommandModalState, draft: CommandDraft, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val problem = draft.problem()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(draft.title(), style = MaterialTheme.typography.headlineSmall)
            // Not scrolled here: content with its own lazy lists (a date picker's years) can't be.
            Box(Modifier.weight(1f, fill = false)) { draft.Content() }
            (state.error ?: problem)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = state::cancel, enabled = !state.confirming) {
                    Text(stringResource(R.string.command_cancel))
                }
                Button(onClick = onConfirm, enabled = problem == null && !state.confirming) {
                    if (state.confirming) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.command_confirm))
                    }
                }
            }
        }
    }
}
