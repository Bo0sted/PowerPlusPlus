package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Something drawn on the screenshot. All coordinates and sizes are image pixels; colors are ARGB. */
sealed interface Mark {
    data class Pen(val points: List<Offset>, val color: Int, val width: Float) : Mark

    data class Arrow(val start: Offset, val end: Offset, val color: Int, val width: Float) : Mark

    data class Box(val rect: Rect, val color: Int, val width: Float) : Mark

    data class Ellipse(val rect: Rect, val color: Int, val width: Float) : Mark

    /**
     * Text placed at [position] (top-left). [id] identifies it for selection, moving and editing.
     * With [background], a solid box in [color] sits behind the text, which is then drawn in black
     * or white, whichever contrasts more.
     */
    data class Label(
        val id: Long,
        val position: Offset,
        val text: String,
        val color: Int,
        val size: Float,
        val background: Boolean = false,
    ) : Mark

    /** Shows the blurred or pixelated image (per [kind] and [strength], 0..1) inside [area]. */
    data class Effect(val kind: EffectKind, val strength: Float, val area: EffectArea) : Mark
}

enum class EffectKind { Blur, Pixelate }

sealed interface EffectArea {
    data class Brush(val points: List<Offset>, val diameter: Float) : EffectArea

    data class Box(val rect: Rect) : EffectArea
}

/**
 * The marks in drawing order, with undo and redo. Every edit — adding, moving, recolouring or
 * deleting — is a new snapshot, so all of them can be undone. Immutable.
 */
data class EditHistory(
    val marks: List<Mark> = emptyList(),
    private val past: List<List<Mark>> = emptyList(),
    private val future: List<List<Mark>> = emptyList(),
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Records [next] as the current marks; this clears what could be redone. No-op if unchanged. */
    fun commit(next: List<Mark>) = if (next == marks) this else EditHistory(next, past + listOf(marks), emptyList())

    fun add(mark: Mark) = commit(marks + mark)

    fun updateLabel(id: Long, transform: (Mark.Label) -> Mark.Label) =
        commit(marks.map { if (it is Mark.Label && it.id == id) transform(it) else it })

    fun removeLabel(id: Long) = commit(marks.filterNot { it is Mark.Label && it.id == id })

    fun undo() = if (canUndo) EditHistory(past.last(), past.dropLast(1), future + listOf(marks)) else this

    fun redo() = if (canRedo) EditHistory(future.last(), past + listOf(marks), future.dropLast(1)) else this
}

/** A rectangle spanning two points dragged in any direction. */
fun rectBetween(a: Offset, b: Offset): Rect =
    Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
