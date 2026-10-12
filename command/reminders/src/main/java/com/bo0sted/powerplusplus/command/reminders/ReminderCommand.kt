package com.bo0sted.powerplusplus.command.reminders

import android.content.Context
import com.bo0sted.powerplusplus.assistant.AssistantCommand
import com.bo0sted.powerplusplus.assistant.CommandDraft
import com.bo0sted.powerplusplus.ntfy.NtfyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.ZonedDateTime
import javax.inject.Inject

/** "Remind me at 8pm tomorrow to call Mom": a push notification through ntfy at that time. */
class ReminderCommand @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ntfy: NtfyRepository,
) : AssistantCommand {
    override val id = "reminders"
    override val title = R.string.reminder_command_title
    override val description = R.string.reminder_command_description
    override val available = ntfy.ready
    override val requirement = R.string.reminder_needs_ntfy

    override fun draftFor(transcript: String, now: ZonedDateTime): CommandDraft? =
        ReminderParser.parse(transcript, now.toLocalDateTime())?.let { ReminderDraft(context, ntfy, it, now.toLocalDateTime()) }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ReminderBindings {
    @Binds
    @IntoSet
    fun bind(command: ReminderCommand): AssistantCommand
}
