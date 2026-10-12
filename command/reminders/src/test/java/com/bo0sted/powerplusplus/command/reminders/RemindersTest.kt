package com.bo0sted.powerplusplus.command.reminders

import com.bo0sted.powerplusplus.ntfy.NtfyReceived
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

class RemindersTest {

    private val now = 1_000_000L
    private fun message(id: String?, time: Long, text: String = "Call Mom", event: String = "message", priority: Int = 5) =
        NtfyReceived(event = event, time = time, message = text, priority = priority, sequenceId = id)

    @Test
    fun groupsScheduledMessagesIntoReminders() {
        val reminders = Reminders.group(
            listOf(
                message("powerpp-aaaaaaaaaaaa-2", now + 3_600),
                message("powerpp-aaaaaaaaaaaa-0", now + 7_200),
                message("powerpp-aaaaaaaaaaaa-1", now + 7_200 - 600),
                message("powerpp-bbbbbbbbbbbb-0", now + 60, "Stretch", priority = 0), // Scheduled without a priority.
            ),
            now,
            ZoneOffset.UTC,
        )
        assertEquals(listOf("Stretch", "Call Mom"), reminders.map { it.text })
        val callMom = reminders[1]
        assertEquals(LocalDateTime.ofEpochSecond(now + 7_200, 0, ZoneOffset.UTC), callMom.time)
        assertEquals(listOf(Duration.ofMinutes(10), Duration.ofHours(1)), callMom.early)
        assertEquals((0..2).map { "powerpp-aaaaaaaaaaaa-$it" }, callMom.sequenceIds)
        assertEquals(listOf(5, 5), reminders.map { it.priority })
    }

    @Test
    fun keepsAChosenPriority() {
        val reminders = Reminders.group(listOf(message("powerpp-ffffffffffff-0", now + 60, priority = 3)), now, ZoneOffset.UTC)
        assertEquals(3, reminders.single().priority)
    }

    @Test
    fun ignoresOtherMessagesAndPastOnes() {
        val reminders = Reminders.group(
            listOf(
                message(null, now + 60), // The setup test post, or anything else on the topic.
                message("powerpp-cccccccccccc-0", now - 60), // Already delivered.
                message("powerpp-dddddddddddd-0", now + 60, event = "message_delete"),
                message("powerpp-eeeeeeeeeeee-1", now + 60), // Early reminder left without its reminder.
            ),
            now,
            ZoneOffset.UTC,
        )
        assertEquals(emptyList<Reminder>(), reminders)
    }
}
