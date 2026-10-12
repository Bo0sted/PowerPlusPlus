package com.bo0sted.powerplusplus.ntfy

import android.content.Context
import com.bo0sted.powerplusplus.data.jsonDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A server and topic that Power++ has successfully posted to. */
@Serializable
data class NtfyServer(
    /** `https://host[:port][/path]`, without a trailing slash. */
    val url: String,
    val topic: String,
    /** An access token (`tk_…`); empty for open topics. */
    val token: String = "",
    /** The longest delay the server accepts, measured when saved or re-checked; see [NtfyClient.probeDelayLimit]. */
    val delayLimitSeconds: Long,
)

@Serializable
data class NtfySettings(
    /** The user's switch for the ntfy section; features that need ntfy are off while it's off. */
    val enabled: Boolean = false,
    /** Saved only after a successful post; `null` until then. */
    val server: NtfyServer? = null,
) {
    /** Whether features can post: switched on and a server is saved. */
    val ready: Boolean get() = enabled && server != null
}

/**
 * The ntfy settings. Server details are saved only after a test post to them succeeded and their
 * delay limit was measured; any failure leaves the saved settings unchanged. Kept in no-backup
 * storage because they hold an access token.
 */
@Singleton
class NtfyRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store = jsonDataStore({ File(context.noBackupFilesDir, "ntfy.json") }, NtfySettings.serializer(), NtfySettings())

    val settings: Flow<NtfySettings> = store.data

    val ready: Flow<Boolean> = settings.map { it.ready }

    suspend fun setEnabled(enabled: Boolean) {
        store.updateData { it.copy(enabled = enabled) }
    }

    /**
     * Posts a test message to [url]/[topic], measures the delay limit and, only if both succeed,
     * saves the server. Throws [NtfyException] (or [IllegalArgumentException] for malformed input)
     * otherwise.
     */
    suspend fun save(url: String, topic: String, token: String) {
        val candidate = NtfyServer(normalizeUrl(url), topic.trim(), token.trim(), delayLimitSeconds = 0)
        require(TOPIC.matches(candidate.topic)) { "Topics use only letters, digits, - and _ (up to 64)" }
        NtfyClient.publish(candidate, NtfyMessage(topic = candidate.topic, title = "Power++", message = "ntfy is set up for Power++."))
        val server = candidate.copy(delayLimitSeconds = measureLimit(candidate))
        store.updateData { it.copy(server = server) }
    }

    /** Measures the saved server's delay limit again (after the user changed it on the server). */
    suspend fun refreshDelayLimit() {
        val server = store.data.first().server ?: return
        val limit = measureLimit(server)
        // Only if the server wasn't replaced meanwhile.
        store.updateData { current -> if (current.server == server) current.copy(server = server.copy(delayLimitSeconds = limit)) else current }
    }

    private suspend fun measureLimit(server: NtfyServer): Long =
        NtfyClient.probeDelayLimit(server) ?: throw NtfyException("This server doesn't accept scheduled messages")

    internal companion object {
        /** ntfy's own topic rule (`topicRegex` in the server). */
        val TOPIC = Regex("[-_A-Za-z0-9]{1,64}")

        /** Adds `https://` when no scheme is given and drops trailing slashes; only https is allowed. */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            val url = if ("://" in trimmed) trimmed else "https://$trimmed"
            require(url.startsWith("https://") && url.length > "https://".length) { "Use an https:// server address" }
            return url
        }
    }
}
