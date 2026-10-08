package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Something drawn on the screenshot. All coordinates and sizes are image pixels; colors are ARGB. */
sealed interface Mark {
    data class Pen(val points: List<Offset>, val color: Int, val width: Float) : Mark

    data class Arrow(val start: Offset, val end: Offset, val color: Int, val width: Float) : Mark

    data class Box(val rect: Rect, val color: Int, val width: Float) : Mark

    data class Ellipse(val rect: Rect, val color: Int, val width: Float) : Mark

    data class Label(val position: Offset, val text: String, val color: Int, val size: Float) : Mark

    /** Shows the blurred or pixelated image (per [kind] and [strength], 0..1) inside [area]. */
    data class Effect(val kind: EffectKind, val strength: Float, val area: EffectArea) : Mark
}

enum class EffectKind { Blur, Pixelate }

sealed interface EffectArea {
    data class Brush(val points: List<Offset>, val diameter: Float) : EffectArea

    data class Box(val rect: Rect) : EffectArea
}

/** The marks in drawing order, with undo and redo. Immutable: every edit returns a new history. */
data class EditHistory(val marks: List<Mark> = emptyList(), private val undone: List<Mark> = emptyList()) {
    val canUndo: Boolean get() = marks.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** Adds a mark; this clears what could be redone, as in every editor. */
    fun add(mark: Mark) = EditHistory(marks + mark, emptyList())

    fun undo() = if (canUndo) EditHistory(marks.dropLast(1), undone + marks.last()) else this

    fun redo() = if (canRedo) EditHistory(marks + undone.last(), undone.dropLast(1)) else this
}

/** A rectangle spanning two points dragged in any direction. */
fun rectBetween(a: Offset, b: Offset): Rect =
    Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
