package com.bo0sted.powerplusplus.command.reminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.bo0sted.powerplusplus.assistant.CommandModal
import com.bo0sted.powerplusplus.assistant.CommandModalState
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import com.bo0sted.powerplusplus.ui.PowerTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Upcoming reminders, read from the ntfy server each time it's shown: view, edit, delete, add. */
@AndroidEntryPoint
class RemindersActivity : ComponentActivity() {
    private val viewModel: RemindersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Opened for a new reminder: the same as tapping New reminder (once, not again after rotation).
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_NEW, false)) viewModel.create()
        setContent { PowerTheme { RemindersScreen(onBack = ::finish, viewModel = viewModel) } }
    }

    companion object {
        private const val EXTRA_NEW = "new_reminder"

        /** Upcoming reminders, with the New reminder modal already open. */
        fun newReminderIntent(context: Context): Intent =
            Intent(context, RemindersActivity::class.java).putExtra(EXTRA_NEW, true)
    }
}

@HiltViewModel
internal class RemindersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ntfy: NtfyRepository,
    private val reminders: Reminders,
) : ViewModel() {

    /** `null` while loading. */
    var list by mutableStateOf<List<Reminder>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var deleting by mutableStateOf(false)
        private set

    val modal = CommandModalState()

    /** Reads the reminders from the server again. */
    fun load() {
        error = null
        viewModelScope.launch {
            try {
                list = reminders.upcoming()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                list = list ?: emptyList()
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun edit(reminder: Reminder) = modal.open(ReminderDraft(context, ntfy, reminders, reminder))

    /** A new reminder, starting at the current date and time (there's no spoken time to start from). */
    fun create() = modal.open(ReminderDraft(context, ntfy, reminders, Reminder.new("", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))))

    fun confirm() = modal.confirm(viewModelScope, onDone = ::load)

    fun delete(reminder: Reminder) {
        if (deleting) return
        deleting = true
        error = null
        viewModelScope.launch {
            try {
                reminders.delete(reminder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                deleting = false
                load()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel) {
    // Every time the screen is shown: the server is the only copy.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.load() }
    var confirmDelete by remember { mutableStateOf<Reminder?>(null) }
    val list = viewModel.list

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upcoming_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.upcoming_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.upcoming_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::create,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.upcoming_new)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    viewModel.error?.let { error ->
                        item {
                            Text(
                                stringResource(R.string.upcoming_failed, error),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    if (list.isEmpty() && viewModel.error == null) {
                        item {
                            Text(
                                stringResource(R.string.upcoming_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { reminder ->
                        ReminderRow(reminder, onClick = { viewModel.edit(reminder) }, onDelete = { confirmDelete = reminder })
                        HorizontalDivider()
                    }
                }
            }
            if (viewModel.deleting) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    confirmDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.upcoming_delete_title)) },
            text = { Text(stringResource(R.string.upcoming_delete_body, reminder.text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(reminder)
                    confirmDelete = null
                }) { Text(stringResource(R.string.upcoming_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.upcoming_cancel)) } },
        )
    }

    viewModel.modal.draft?.let { draft ->
        // The same modal the assistant opens over the panel.
        Dialog(
            onDismissRequest = { viewModel.modal.onBack() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            val screenHeight = with(LocalDensity.current) { LocalResources.current.displayMetrics.heightPixels.toDp() }
            CommandModal(
                state = viewModel.modal,
                draft = draft,
                onConfirm = viewModel::confirm,
                modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = screenHeight * 0.85f),
            )
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val whenText = reminder.time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    ListItem(
        headlineContent = { Text(reminder.text) },
        supportingContent = {
            Text(
                if (reminder.early.isEmpty()) {
                    whenText
                } else {
                    whenText + " · " + pluralStringResource(R.plurals.upcoming_early, reminder.early.size, reminder.early.size) +
                        " (" + reminder.early.joinToString { formatDuration(context, it) } + ")"
                },
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.upcoming_delete))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
