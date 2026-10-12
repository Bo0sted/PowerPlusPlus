package com.bo0sted.powerplusplus.command.reminders

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.assistant.CommandDraft
import com.bo0sted.powerplusplus.ntfy.NtfyPriority
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * A reminder being reviewed in the command modal: what, when, and any number of early reminders
 * ("Reminder coming up in 2 hours"). Used for a spoken reminder, a new one from Upcoming reminders,
 * and editing a scheduled one. Confirming hands every notification to ntfy as a scheduled message,
 * so they arrive on time even if this phone is off.
 */
internal class ReminderDraft(
    private val context: Context,
    private val ntfy: NtfyRepository,
    private val reminders: Reminders,
    private val reminder: Reminder,
) : CommandDraft {

    private var text by mutableStateOf(reminder.text)
    private var date by mutableStateOf(reminder.time.toLocalDate())
    private var time by mutableStateOf(reminder.time.toLocalTime())
    private val early = mutableStateListOf<EarlyReminder>().apply { addAll(reminder.early.map(EarlyReminder::of)) }
    private var priority by mutableIntStateOf(reminder.priority)
    private var page by mutableStateOf(Page.Form)

    private enum class Page { Form, Date, Time }

    private data class EarlyReminder(val amount: String, val unit: EarlyUnit) {
        val duration: Duration? get() = amount.toLongOrNull()?.takeIf { it > 0 }?.let { unit.of(it) }

        companion object {
            /** In the largest unit that divides it exactly. */
            fun of(duration: Duration): EarlyReminder = when {
                duration.toMinutes() % (24 * 60) == 0L -> EarlyReminder("${duration.toDays()}", EarlyUnit.Days)
                duration.toMinutes() % 60 == 0L -> EarlyReminder("${duration.toHours()}", EarlyUnit.Hours)
                else -> EarlyReminder("${duration.toMinutes()}", EarlyUnit.Minutes)
            }
        }
    }

    enum class EarlyUnit(val of: (Long) -> Duration) {
        Minutes(Duration::ofMinutes), Hours(Duration::ofHours), Days(Duration::ofDays)
    }

    private fun at(): Instant = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant()

    @Composable
    override fun title() = stringResource(if (reminder.isScheduled) R.string.reminder_edit_title else R.string.reminder_title)

    override fun onBack(): Boolean {
        if (page == Page.Form) return false
        page = Page.Form
        return true
    }

    @Composable
    override fun problem(): String? {
        val limit = ntfy.settings.collectAsState(initial = null).value?.server?.delayLimitSeconds
        val earliest = Instant.now().plusSeconds(MIN_DELAY_SECONDS)
        val at = at()
        return when {
            page != Page.Form -> "" // Confirm waits until the date or time is picked.
            text.isBlank() -> stringResource(R.string.reminder_problem_text)
            at.isBefore(earliest) -> stringResource(R.string.reminder_problem_past)
            limit != null && at.isAfter(Instant.now().plusSeconds(limit)) ->
                stringResource(R.string.reminder_problem_limit, formatDuration(context, Duration.ofSeconds(limit)))
            early.any { it.duration == null } -> stringResource(R.string.reminder_problem_early_amount)
            early.any { entry -> entry.duration?.let { at.minus(it).isBefore(earliest) } == true } -> stringResource(R.string.reminder_problem_early_past)
            else -> null
        }
    }

    override suspend fun confirm() {
        reminders.schedule(
            reminder.copy(
                text = text.trim(),
                time = LocalDateTime.of(date, time),
                early = early.mapNotNull { it.duration },
                priority = priority,
            ),
        )
    }

    @Composable
    override fun Content() {
        when (page) {
            Page.Form -> Form()
            Page.Date -> DatePage()
            Page.Time -> TimePage()
        }
    }

    @Composable
    private fun Form() {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.reminder_text_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page = Page.Date }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Event, contentDescription = null)
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), Modifier.weight(1f, fill = false))
                }
                OutlinedButton(onClick = { page = Page.Time }) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                }
            }
            QUICK_TIMES.forEach { row -> QuickTimeRow(row) }

            Text(stringResource(R.string.reminder_priority), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                NtfyPriority.ALL.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = priority == level,
                        onClick = { priority = level },
                        shape = SegmentedButtonDefaults.itemShape(index, NtfyPriority.ALL.count()),
                        icon = {},
                    ) { Text(stringResource(PRIORITY_LABELS[index]), maxLines = 1) }
                }
            }

            Text(stringResource(R.string.reminder_early_heading), style = MaterialTheme.typography.titleSmall)
            early.forEachIndexed { index, entry ->
                EarlyRow(
                    entry = entry,
                    onChange = { early[index] = it },
                    onRemove = { early.removeAt(index) },
                )
            }
            FilledTonalButton(onClick = { early += EarlyReminder("1", EarlyUnit.Hours) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.reminder_early_add))
            }
        }
    }

    /** One-off shortcuts: each sets the date and time to now plus its amount; nothing stays selected. */
    @Composable
    private fun QuickTimeRow(row: List<Pair<Duration, Int>>) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { (amount, label) ->
                FilledTonalButton(
                    onClick = {
                        val at = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plus(amount)
                        date = at.toLocalDate()
                        time = at.toLocalTime()
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
        }
    }

    @Composable
    private fun EarlyRow(entry: EarlyReminder, onChange: (EarlyReminder) -> Unit, onRemove: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry.amount,
                onValueChange = { value -> onChange(entry.copy(amount = value.filter(Char::isDigit).take(4))) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                EarlyUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = entry.unit == unit,
                        onClick = { onChange(entry.copy(unit = unit)) },
                        shape = SegmentedButtonDefaults.itemShape(index, EarlyUnit.entries.size),
                        icon = {},
                    ) { Text(stringResource(unit.label)) }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.reminder_early_remove))
            }
        }
        Text(
            entry.duration?.let { stringResource(R.string.reminder_early_before, formatDuration(context, it)) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DatePage() {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                // The picker works in UTC midnights.
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        Column {
            DatePicker(state = state, title = null, headline = null, showModeToggle = false)
            PageButtons(onDone = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                page = Page.Form
            })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TimePage() {
        val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimePicker(state = state)
            PageButtons(onDone = {
                time = LocalTime.of(state.hour, state.minute)
                page = Page.Form
            })
        }
    }

    @Composable
    private fun PageButtons(onDone: () -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { page = Page.Form }) { Text(stringResource(R.string.reminder_page_back)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.reminder_page_done)) }
        }
    }

    private companion object {
        /** ntfy refuses scheduled messages less than 10 s ahead (its `message-delay-min`). */
        const val MIN_DELAY_SECONDS = 10L

        /** The quick-time buttons, in two rows (user, 2026-10-12). */
        val QUICK_TIMES = listOf(
            listOf(
                Duration.ofMinutes(10) to R.string.reminder_quick_10m,
                Duration.ofMinutes(30) to R.string.reminder_quick_30m,
                Duration.ofHours(1) to R.string.reminder_quick_1h,
                Duration.ofHours(2) to R.string.reminder_quick_2h,
                Duration.ofHours(3) to R.string.reminder_quick_3h,
                Duration.ofHours(6) to R.string.reminder_quick_6h,
                Duration.ofHours(12) to R.string.reminder_quick_12h,
            ),
            listOf(
                Duration.ofDays(1) to R.string.reminder_quick_1d,
                Duration.ofDays(2) to R.string.reminder_quick_2d,
                Duration.ofDays(3) to R.string.reminder_quick_3d,
            ),
        )

        /** For [NtfyPriority.MIN] to [NtfyPriority.MAX], in order. */
        val PRIORITY_LABELS = listOf(
            R.string.reminder_priority_min,
            R.string.reminder_priority_low,
            R.string.reminder_priority_default,
            R.string.reminder_priority_high,
            R.string.reminder_priority_max,
        )
    }
}

private val ReminderDraft.EarlyUnit.label: Int
    get() = when (this) {
        ReminderDraft.EarlyUnit.Minutes -> R.string.reminder_unit_minutes
        ReminderDraft.EarlyUnit.Hours -> R.string.reminder_unit_hours
        ReminderDraft.EarlyUnit.Days -> R.string.reminder_unit_days
    }
