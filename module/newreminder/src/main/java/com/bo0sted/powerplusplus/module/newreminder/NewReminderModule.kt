package com.bo0sted.powerplusplus.module.newreminder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import com.bo0sted.powerplusplus.assistant.CommandModule
import com.bo0sted.powerplusplus.command.reminders.ReminderCommand
import com.bo0sted.powerplusplus.module.PanelModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Opens the reminder modal over the panel, the same draft as Upcoming reminders' New reminder button. */
class NewReminderModule @Inject constructor(private val reminders: ReminderCommand) : CommandModule {
    override val typeId = "new_reminder"
    override val title = R.string.module_new_reminder
    override val icon = Icons.Rounded.AddAlarm

    override fun newDraft() = reminders.newDraft()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NewReminderBindings {
    @Binds
    @IntoSet
    fun bind(module: NewReminderModule): PanelModule
}
