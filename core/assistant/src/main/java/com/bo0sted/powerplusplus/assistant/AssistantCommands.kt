package com.bo0sted.powerplusplus.assistant

import android.content.Context
import androidx.datastore.dataStoreFile
import com.bo0sted.powerplusplus.data.jsonDataStore
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class AssistantSettings(val enabledCommands: Set<String> = emptySet())

/**
 * Every [AssistantCommand] in the build, and the user's switch for each. A command is on only while
 * the user switched it on **and** it's [AssistantCommand.available]; the switch is kept either way.
 */
@Singleton
class AssistantCommands @Inject constructor(
    @ApplicationContext context: Context,
    commands: Set<@JvmSuppressWildcards AssistantCommand>,
) {
    private val store = jsonDataStore({ context.dataStoreFile("assistant.json") }, AssistantSettings.serializer(), AssistantSettings())

    val all: List<AssistantCommand> = commands.sortedBy { it.id }

    init {
        require(all.map { it.id }.toSet().size == all.size) { "Duplicate command id in ${all.map { it.id }}" }
    }

    fun isOn(command: AssistantCommand): Flow<Boolean> =
        combine(store.data, command.available) { settings, available -> available && command.id in settings.enabledCommands }

    suspend fun setOn(command: AssistantCommand, on: Boolean) {
        store.updateData { it.copy(enabledCommands = if (on) it.enabledCommands + command.id else it.enabledCommands - command.id) }
    }

    /** The commands that are on right now. */
    val active: Flow<List<AssistantCommand>> =
        if (all.isEmpty()) flowOf(emptyList())
        else combine(all.map { command -> isOn(command) }) { on -> all.filterIndexed { index, _ -> on[index] } }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AssistantCommandSet {
    /** Lets the set be empty while no `:command:*` is on the classpath. */
    @Multibinds
    fun commands(): Set<AssistantCommand>
}
