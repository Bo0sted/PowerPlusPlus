package com.bo0sted.powerplusplus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [PanelConfig]. Every edit is written immediately and every observer of
 * [config] (the running panel included) sees it at once — there is no separate "apply" step.
 */
@Singleton
class PanelRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store: DataStore<PanelConfig> =
        jsonDataStore({ context.dataStoreFile("panel.json") }, PanelConfig.serializer(), PanelConfig())

    val config: Flow<PanelConfig> = store.data

    suspend fun edit(transform: (PanelConfig) -> PanelConfig) {
        store.updateData(transform)
    }
}
