package com.bo0sted.powerplusplus.command.reminders

import android.content.Context
import android.content.Intent
import com.bo0sted.powerplusplus.assistant.AssistantCommand
import com.bo0sted.powerplusplus.assistant.CommandDraft
import com.bo0sted.powerplusplus.assistant.CommandScreen
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** "Remind me at 8pm tomorrow to call Mom": a push notification through ntfy at that time. */
class ReminderCommand @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val ntfy: NtfyRepository,
    private val reminders: Reminders,
) : AssistantCommand {
    override val id = "reminders"
    override val title = R.string.reminder_command_title
    override val description = R.string.reminder_command_description
    override val available = ntfy.ready
    override val requirement = R.string.reminder_needs_ntfy

    override val screen = CommandScreen(R.string.upcoming_title, R.string.upcoming_description) { from ->
        from.startActivity(Intent(from, RemindersActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A new reminder with nothing spoken: empty, at the current date and time. */
    fun newDraft(): CommandDraft =
        ReminderDraft(context, ntfy, reminders, Reminder.new("", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)))

    override fun draftFor(transcript: String, now: ZonedDateTime): CommandDraft? {
        val parsed = ReminderParser.parse(transcript, now.toLocalDateTime()) ?: return null
        // No time spoken: the next full hour, to be adjusted.
        val time = parsed.time ?: now.toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return ReminderDraft(context, ntfy, reminders, Reminder.new(parsed.text, time))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ReminderBindings {
    @Binds
    @IntoSet
    fun bind(command: ReminderCommand): AssistantCommand
}
