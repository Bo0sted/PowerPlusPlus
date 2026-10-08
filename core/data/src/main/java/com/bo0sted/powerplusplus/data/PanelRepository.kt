package com.bo0sted.powerplusplus.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [PanelConfig]. Every edit is written immediately and every observer of
 * [config] (the running panel included) sees it at once — there is no separate "apply" step.
 */
@Singleton
class PanelRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store: DataStore<PanelConfig> = DataStoreFactory.create(
        serializer = PanelConfigSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { PanelConfig() },
        produceFile = { context.dataStoreFile("panel.json") },
    )

    val config: Flow<PanelConfig> = store.data

    suspend fun edit(transform: (PanelConfig) -> PanelConfig) {
        store.updateData(transform)
    }
}

private object PanelConfigSerializer : Serializer<PanelConfig> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = PanelConfig()

    override suspend fun readFrom(input: InputStream): PanelConfig =
        try {
            json.decodeFromString(PanelConfig.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable panel config", e)
        }

    override suspend fun writeTo(t: PanelConfig, output: OutputStream) {
        output.write(json.encodeToString(PanelConfig.serializer(), t).encodeToByteArray())
    }
}
