package com.bo0sted.powerplusplus.command.reminders

import android.content.Context
import com.bo0sted.powerplusplus.ntfy.NtfyClient
import com.bo0sted.powerplusplus.ntfy.NtfyException
import com.bo0sted.powerplusplus.ntfy.NtfyMessage
import com.bo0sted.powerplusplus.ntfy.NtfyReceived
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import com.bo0sted.powerplusplus.ntfy.NtfyServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A reminder: its main notification at [time] plus one early notification per entry of [early]
 * (how long before). [sequenceIds] are the scheduled messages it has on the server (none yet for a
 * new one).
 */
internal data class Reminder(
    val id: String,
    val text: String,
    val time: LocalDateTime,
    val early: List<Duration> = emptyList(),
    val sequenceIds: List<String> = emptyList(),
) {
    val isScheduled: Boolean get() = sequenceIds.isNotEmpty()

    companion object {
        fun new(text: String, time: LocalDateTime) = Reminder(UUID.randomUUID().toString().replace("-", "").take(12), text, time)
    }
}

/**
 * Reminders live only on the ntfy server, as scheduled messages: Power++ stores nothing. Each
 * reminder's messages have the sequence IDs `powerpp-<id>-0` (the reminder) and `-1`, `-2`… (early
 * reminders), which is how they're found, replaced and cancelled.
 */
@Singleton
internal class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ntfy: NtfyRepository,
) {
    /** The reminders still to come, soonest first, read from the server now. */
    suspend fun upcoming(): List<Reminder> {
        val now = Instant.now().epochSecond
        return group(NtfyClient.poll(server(), since = now), now, ZoneId.systemDefault())
    }

    /**
     * Schedules [reminder]: publishing under its existing sequence IDs replaces those messages, and
     * IDs it no longer uses (fewer early reminders) are cancelled. Safe to repeat after a failure.
     */
    suspend fun schedule(reminder: Reminder) {
        val server = server()
        val at = reminder.time.atZone(ZoneId.systemDefault()).toInstant()
        val messages = listOf(
            NtfyMessage(
                topic = server.topic,
                title = context.getString(R.string.reminder_notification_title),
                message = reminder.text,
                tags = listOf("alarm_clock"),
                delay = NtfyClient.delayAt(at),
                sequenceId = sequenceId(reminder.id, 0),
            ),
        ) + reminder.early.mapIndexed { index, before ->
            NtfyMessage(
                topic = server.topic,
                title = context.getString(R.string.reminder_early_title, formatDuration(context, before)),
                message = reminder.text,
                tags = listOf("hourglass_flowing_sand"),
                delay = NtfyClient.delayAt(at.minus(before)),
                sequenceId = sequenceId(reminder.id, index + 1),
            )
        }
        messages.forEach { NtfyClient.publish(server, it) }
        val used = messages.mapNotNull { it.sequenceId }.toSet()
        reminder.sequenceIds.filterNot { it in used }.forEach { NtfyClient.delete(server, it) }
    }

    /** Cancels every scheduled message of [reminder]. */
    suspend fun delete(reminder: Reminder) {
        val server = server()
        reminder.sequenceIds.forEach { NtfyClient.delete(server, it) }
    }

    private suspend fun server(): NtfyServer =
        ntfy.settings.first().takeIf { it.enabled }?.server ?: throw NtfyException(context.getString(R.string.reminder_needs_ntfy))

    companion object {
        private val SEQUENCE_ID = Regex("""powerpp-([0-9a-f]{12})-(\d+)""")

        fun sequenceId(id: String, index: Int) = "powerpp-$id-$index"

        /**
         * Rebuilds reminders from polled messages: scheduled ones (after [now]) with Power++ sequence
         * IDs, grouped by reminder. A group whose main message is gone (delivered or deleted) is
         * skipped. Pure; unit-tested.
         */
        fun group(messages: List<NtfyReceived>, now: Long, zone: ZoneId): List<Reminder> =
            messages
                .filter { it.event == "message" && it.time > now }
                .mapNotNull { message -> SEQUENCE_ID.matchEntire(message.sequenceId.orEmpty())?.let { it to message } }
                .groupBy({ (match, _) -> match.groupValues[1] }, { (match, message) -> match.groupValues[2].toInt() to message })
                .mapNotNull { (id, parts) ->
                    val byIndex = parts.toMap()
                    val main = byIndex[0] ?: return@mapNotNull null
                    Reminder(
                        id = id,
                        text = main.message,
                        time = LocalDateTime.ofInstant(Instant.ofEpochSecond(main.time), zone),
                        early = byIndex.filterKeys { it > 0 }.toSortedMap().values.map { Duration.ofSeconds(main.time - it.time) },
                        sequenceIds = byIndex.keys.sorted().map { sequenceId(id, it) },
                    )
                }
                .sortedBy { it.time }
    }
}

/** "2 hours", "1 day 6 hours", "45 minutes": the largest two units. */
internal fun formatDuration(context: Context, duration: Duration): String {
    val parts = listOf(
        R.plurals.reminder_days to duration.toDays(),
        R.plurals.reminder_hours to duration.toHoursPart().toLong(),
        R.plurals.reminder_minutes to duration.toMinutesPart().toLong(),
    ).filter { it.second > 0 }.take(2)
    if (parts.isEmpty()) return context.resources.getQuantityString(R.plurals.reminder_minutes, 0, 0)
    return parts.joinToString(" ") { (res, n) -> context.resources.getQuantityString(res, n.toInt(), n.toInt()) }
}
