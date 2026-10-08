package com.bo0sted.powerplusplus.module.screenshot.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the image, the edit history and the processed blur/pixelate images. Lives across
 * configuration changes, so a rotation doesn't lose the edit.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    /** `null` if the editor was started without an image (e.g. after process death). */
    private val source: Bitmap? = EditorSession.take()
    val image: ImageBitmap? = source?.asImageBitmap()

    var history by mutableStateOf(EditHistory())
        private set

    /** Processed images keyed by [EditorEffects.cacheKey]. */
    val effects = mutableStateMapOf<Pair<EffectKind, Int>, ImageBitmap>()
    private val computing = mutableSetOf<Pair<EffectKind, Int>>()

    private val settingsStore = EditorSettingsStore(application)
    val settings: StateFlow<EditorSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettings())

    fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun add(mark: Mark) {
        history = history.add(mark)
    }

    fun undo() {
        history = history.undo()
    }

    fun redo() {
        history = history.redo()
    }

    /** Starts processing [kind] at [strength] in the background if it isn't cached yet. */
    fun prepareEffect(kind: EffectKind, strength: Float) {
        val base = source ?: return
        val key = EditorEffects.cacheKey(kind, strength)
        if (key in effects || !computing.add(key)) return
        viewModelScope.launch {
            val processed = withContext(Dispatchers.Default) { EditorEffects.apply(base, kind, strength) }
            effects[key] = processed.asImageBitmap()
            computing -= key
        }
    }

    /** Flattens the image and all marks at full resolution. Runs off the main thread. */
    suspend fun render(): Bitmap? {
        val base = source ?: return null
        val image = image ?: return null
        val marks = history.marks
        return withContext(Dispatchers.Default) {
            val ready = HashMap(effects)
            val extra = ArrayList<Bitmap>()
            marks.filterIsInstance<Mark.Effect>().forEach { effect ->
                val key = EditorEffects.cacheKey(effect.kind, effect.strength)
                if (key !in ready) {
                    val processed = EditorEffects.apply(base, effect.kind, effect.strength)
                    if (processed !== base) extra += processed
                    ready[key] = processed.asImageBitmap()
                }
            }
            val output = ImageBitmap(base.width, base.height)
            val measurer = TextMeasurer(createFontFamilyResolver(getApplication()), Density(1f), LayoutDirection.Ltr)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(output), Size(base.width.toFloat(), base.height.toFloat())) {
                drawEdits(image, marks, ready, measurer)
            }
            extra.forEach { it.recycle() }
            output.asAndroidBitmap()
        }
    }

    override fun onCleared() {
        effects.values.map { it.asAndroidBitmap() }.filter { it !== source }.forEach { it.recycle() }
        source?.recycle()
    }
}
