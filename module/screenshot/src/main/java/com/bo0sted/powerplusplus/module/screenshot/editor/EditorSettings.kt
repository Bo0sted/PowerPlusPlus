package com.bo0sted.powerplusplus.module.screenshot.editor

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Editor choices that persist between sessions. Sizes are in dp so they look the same on any image. */
data class EditorSettings(
    val color: Int = DEFAULT_COLOR,
    val strokeDp: Float = 4f,
    val textDp: Float = 24f,
    val brushDp: Float = 40f,
    val blurStrength: Float = 0.5f,
    val pixelateStrength: Float = 0.5f,
    val effectArea: EffectAreaMode = EffectAreaMode.Brush,
) {
    companion object {
        const val DEFAULT_COLOR = 0xFFF44336.toInt()
        val STROKE_RANGE = 1f..24f
        val TEXT_RANGE = 12f..96f
        val BRUSH_RANGE = 8f..160f
    }
}

enum class EffectAreaMode { Brush, Rectangle }

private val Context.editorStore by preferencesDataStore(name = "screenshot_editor")

/** Reads and writes [EditorSettings]; every change is saved immediately. */
class EditorSettingsStore(context: Context) {
    private val store = context.applicationContext.editorStore

    val settings: Flow<EditorSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (EditorSettings) -> EditorSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[COLOR] = next.color
            prefs[STROKE] = next.strokeDp
            prefs[TEXT] = next.textDp
            prefs[BRUSH] = next.brushDp
            prefs[BLUR] = next.blurStrength
            prefs[PIXELATE] = next.pixelateStrength
            prefs[AREA] = next.effectArea.name
        }
    }

    private fun Preferences.toSettings(): EditorSettings {
        val defaults = EditorSettings()
        return EditorSettings(
            color = this[COLOR] ?: defaults.color,
            strokeDp = (this[STROKE] ?: defaults.strokeDp).coerceIn(EditorSettings.STROKE_RANGE),
            textDp = (this[TEXT] ?: defaults.textDp).coerceIn(EditorSettings.TEXT_RANGE),
            brushDp = (this[BRUSH] ?: defaults.brushDp).coerceIn(EditorSettings.BRUSH_RANGE),
            blurStrength = (this[BLUR] ?: defaults.blurStrength).coerceIn(0f, 1f),
            pixelateStrength = (this[PIXELATE] ?: defaults.pixelateStrength).coerceIn(0f, 1f),
            effectArea = EffectAreaMode.entries.firstOrNull { it.name == this[AREA] } ?: defaults.effectArea,
        )
    }

    private companion object {
        val COLOR = intPreferencesKey("color")
        val STROKE = floatPreferencesKey("stroke_dp")
        val TEXT = floatPreferencesKey("text_dp")
        val BRUSH = floatPreferencesKey("brush_dp")
        val BLUR = floatPreferencesKey("blur_strength")
        val PIXELATE = floatPreferencesKey("pixelate_strength")
        val AREA = stringPreferencesKey("effect_area")
    }
}
