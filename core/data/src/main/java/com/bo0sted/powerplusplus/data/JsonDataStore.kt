package com.bo0sted.powerplusplus.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * A DataStore holding one kotlinx-serializable value as JSON in [file]. Unknown keys are ignored, so
 * older builds read newer files; an unreadable file is replaced by [defaultValue].
 */
fun <T> jsonDataStore(file: () -> File, serializer: KSerializer<T>, defaultValue: T): DataStore<T> =
    DataStoreFactory.create(
        serializer = JsonSerializer(serializer, defaultValue),
        corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
        produceFile = file,
    )

private class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            json.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
