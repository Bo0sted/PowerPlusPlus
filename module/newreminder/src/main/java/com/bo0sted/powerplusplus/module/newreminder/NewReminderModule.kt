package com.bo0sted.powerplusplus.module.newreminder

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import com.bo0sted.powerplusplus.command.reminders.RemindersActivity
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Opens Upcoming reminders with the New reminder modal, exactly like its New reminder button. The
 * panel is already closed; the system binds accessibility services with
 * `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`, which allows the start.
 */
class NewReminderModule @Inject constructor() : ActionModule {
    override val typeId = "new_reminder"
    override val title = R.string.module_new_reminder
    override val icon = Icons.Rounded.AddAlarm

    override suspend fun run(host: ModuleHost) {
        host.service.startActivity(RemindersActivity.newReminderIntent(host.service).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NewReminderBindings {
    @Binds
    @IntoSet
    fun bind(module: NewReminderModule): PanelModule
}
