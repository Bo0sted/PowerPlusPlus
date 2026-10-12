package com.bo0sted.powerplusplus.assistant

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime

/**
 * A spoken command the assistant understands. Implementations live in their own `:command:*`
 * Gradle module and are contributed with a Hilt `@IntoSet` binding of [AssistantCommand]; nothing
 * else refers to a concrete command. Each command declares what it needs through [available], so
 * the assistant itself depends on nothing but speech recognition.
 */
interface AssistantCommand {
    /** Stable identifier persisted in the settings. Never change it once released. */
    val id: String

    @get:StringRes
    val title: Int

    @get:StringRes
    val description: Int

    /** Whether everything the command needs is set up; it can't be switched on otherwise. */
    val available: Flow<Boolean>

    /** What to set up while [available] is false, e.g. "Needs ntfy". */
    @get:StringRes
    val requirement: Int

    /** A draft for the command modal if [transcript] asks for this command, else `null`. */
    fun draftFor(transcript: String, now: ZonedDateTime): CommandDraft?

    /** A screen of its own, listed in the assistant's settings (e.g. upcoming reminders); usable while [available]. */
    val screen: CommandScreen? get() = null
}

/** A command's own screen: its name in the settings, and how to open it. */
class CommandScreen(@get:StringRes val title: Int, @get:StringRes val description: Int, val open: (Context) -> Unit)

/**
 * A recognised command the user reviews and adjusts in the shared command modal before it runs.
 * Holds its own Compose state.
 */
interface CommandDraft {
    @Composable
    fun title(): String

    /**
     * The editable settings, shown in the modal. Text fields are fine: the modal makes room for the
     * keyboard. The modal limits the height; long content scrolls itself.
     */
    @Composable
    fun Content()

    /** Why it can't be confirmed yet (shown above the buttons unless blank; Confirm disabled), or `null`. */
    @Composable
    fun problem(): String?

    /** Steps back within [Content] (e.g. closes a date picker); false if there's nothing to go back from. */
    fun onBack(): Boolean = false

    /** Runs the command. Throws with a message for the user if it fails; the modal then stays open. */
    suspend fun confirm()
}
