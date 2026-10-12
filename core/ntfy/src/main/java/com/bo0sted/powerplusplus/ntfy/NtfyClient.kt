package com.bo0sted.powerplusplus.ntfy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** A message for ntfy's JSON publishing API (`POST /` on the server). */
@Serializable
data class NtfyMessage(
    val topic: String,
    val message: String,
    val title: String? = null,
    val tags: List<String>? = null,
    /** Unix seconds; ntfy holds the message until then ("scheduled delivery"). */
    val delay: String? = null,
    /** Publishing again with the same ID replaces a scheduled message, so retries never duplicate. */
    @SerialName("sequence_id") val sequenceId: String? = null,
)

/** A message as the server returns it when polled (only the fields Power++ reads). */
@Serializable
data class NtfyReceived(
    val event: String,
    /** Unix seconds; for a scheduled message, when it will be delivered. */
    val time: Long,
    val message: String = "",
    val title: String = "",
    @SerialName("sequence_id") val sequenceId: String? = null,
)

/** Something the server refused, with its own explanation; or a network failure ([code] null). */
class NtfyException(message: String, val code: Int? = null, cause: Throwable? = null) : IOException(message, cause)

/**
 * The few ntfy calls Power++ makes, verified against the ntfy server source (2026-10-12). All calls
 * block on the network; they run on [Dispatchers.IO].
 */
object NtfyClient {

    /** Publishes [message]; throws [NtfyException] with the server's reason if it isn't accepted. */
    suspend fun publish(server: NtfyServer, message: NtfyMessage) {
        val (status, body) = request(server, "POST", server.url, json.encodeToString(NtfyMessage.serializer(), message), emptyMap())
        if (status != HttpURLConnection.HTTP_OK) throw refusal(status, body)
    }

    /**
     * Every message on the topic at or after [since] (Unix seconds), **including scheduled ones not
     * delivered yet** (`poll=1&sched=1`), oldest first. Power++ keeps no copy of what it scheduled:
     * this is how it reads them back.
     */
    suspend fun poll(server: NtfyServer, since: Long): List<NtfyReceived> {
        val (status, body) = request(server, "GET", "${server.url}/${server.topic}/json?poll=1&sched=1&since=$since", null, emptyMap())
        if (status != HttpURLConnection.HTTP_OK) throw refusal(status, body)
        return body.lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString(NtfyReceived.serializer(), it) }.toList()
    }

    /** Cancels the scheduled message [sequenceId] (`DELETE /<topic>/<sequence_id>`). */
    suspend fun delete(server: NtfyServer, sequenceId: String) {
        val (status, body) = request(server, "DELETE", "${server.url}/${server.topic}/$sequenceId", null, emptyMap())
        if (status != HttpURLConnection.HTTP_OK) throw refusal(status, body)
    }

    /**
     * The longest delay [server] accepts for scheduled messages, in seconds (minute precision), up
     * to [MAX_PROBED_SECONDS]; `null` if it accepts none.
     *
     * ntfy has no API for its `message-delay-limit`, so it's measured: each probe is a publish with
     * a delay and a deliberately invalid `X-Actions` header. The server checks the delay first
     * (error 40006 when too far ahead) and the actions after it (error 40018), so the answer tells
     * whether the delay was accepted, and the message is always refused: nothing is ever stored or
     * delivered. A binary search needs about 23 probes.
     */
    suspend fun probeDelayLimit(server: NtfyServer): Long? = findDelayLimit { seconds ->
        val headers = mapOf("X-Delay" to "${seconds}s", "X-Actions" to "invalid")
        val (status, body) = request(server, "POST", "${server.url}/${server.topic}", "Power++ delay check", headers)
        when (val code = errorCode(body)) {
            ERROR_ACTIONS_INVALID -> true
            ERROR_DELAY_TOO_LARGE -> false
            else -> throw refusal(status, body, code)
        }
    }

    /** See [probeDelayLimit]: the largest whole number of minutes [accepts] says yes to, in seconds. */
    internal suspend fun findDelayLimit(accepts: suspend (seconds: Long) -> Boolean): Long? {
        if (accepts(MAX_PROBED_SECONDS)) return MAX_PROBED_SECONDS
        var accepted = 0L // Minutes known to be accepted (0: none yet).
        var refused = MAX_PROBED_SECONDS / 60 // Minutes known to be refused.
        while (refused - accepted > 1) {
            val middle = (accepted + refused) / 2
            if (accepts(middle * 60)) accepted = middle else refused = middle
        }
        return if (accepted == 0L) null else accepted * 60
    }

    /** The scheduled-delivery value for [time]. */
    fun delayAt(time: Instant): String = time.epochSecond.toString()

    private suspend fun request(server: NtfyServer, method: String, url: String, body: String?, headers: Map<String, String>): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    if (server.token.isNotBlank()) setRequestProperty("Authorization", "Bearer ${server.token}")
                    headers.forEach(::setRequestProperty)
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    }
                }
                try {
                    if (body != null) connection.outputStream.use { it.write(body.encodeToByteArray()) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    status to (stream?.use { it.readBytes().decodeToString() } ?: "")
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                throw NtfyException(e.message ?: e.javaClass.simpleName, cause = e)
            }
        }

    private fun refusal(status: Int, body: String, code: Int? = errorCode(body)): NtfyException {
        val reason = runCatching { json.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
        return NtfyException(reason?.takeIf { it.isNotBlank() } ?: "HTTP $status", code ?: status)
    }

    private fun errorCode(body: String): Int? =
        try {
            json.decodeFromString(ErrorBody.serializer(), body).code
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private data class ErrorBody(val code: Int? = null, val error: String? = null)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Ten years: beyond that, the server is treated as having no practical limit. */
    const val MAX_PROBED_SECONDS = 10L * 365 * 24 * 60 * 60

    private const val ERROR_DELAY_TOO_LARGE = 40006
    private const val ERROR_ACTIONS_INVALID = 40018
    private const val TIMEOUT_MS = 15_000
}
