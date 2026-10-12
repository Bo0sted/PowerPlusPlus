package com.bo0sted.powerplusplus.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a [RemoteFile] into a directory, accepting it only if its size and SHA-256 match.
 *
 * Bytes go to `<name>.part` first and are resumed with an HTTP Range request if interrupted (all
 * pinned hosts answer 206); a host that ignores the range restarts the file. Each URL is tried in
 * order. Only a verified file is renamed into place, so a file with the final name is always good.
 */
object VerifiedDownloader {

    /** @param onProgress bytes of this file on disk so far. */
    suspend fun download(file: RemoteFile, directory: File, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(directory, file.name)
        if (target.isFile && target.length() == file.sizeBytes && sha256(target) == file.sha256) return@withContext target
        val partial = File(directory, file.name + ".part")
        var lastError: Exception? = null
        for (url in file.urls) {
            try {
                fetch(url, partial, file.sizeBytes, onProgress)
                if (partial.length() != file.sizeBytes || sha256(partial) != file.sha256) {
                    partial.delete() // Wrong bytes: never resume from them.
                    throw IOException("${file.name}: size or SHA-256 mismatch from $url")
                }
                if (!partial.renameTo(target)) throw IOException("${file.name}: rename failed")
                return@withContext target
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("${file.name}: no URL")
    }

    private suspend fun fetch(url: String, partial: File, expectedSize: Long, onProgress: (Long) -> Unit) {
        val existing = if (partial.isFile && partial.length() < expectedSize) partial.length() else 0L
        if (existing == 0L) partial.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> existing > 0
                HttpURLConnection.HTTP_OK -> false // Range ignored: start over.
                else -> throw IOException("HTTP ${connection.responseCode} from $url")
            }
            var written = if (append) existing else 0L
            onProgress(written)
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val TIMEOUT_MS = 30_000
    private const val BUFFER = 64 * 1024
}
