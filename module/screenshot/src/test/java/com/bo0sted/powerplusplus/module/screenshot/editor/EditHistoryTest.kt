package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private val a = Mark.Box(Rect(0f, 0f, 1f, 1f), color = 0, width = 1f)
    private val b = Mark.Box(Rect(1f, 1f, 2f, 2f), color = 0, width = 1f)
    private val c = Mark.Box(Rect(2f, 2f, 3f, 3f), color = 0, width = 1f)
    private val label = Mark.Label(id = 7, position = Offset(10f, 10f), text = "hi", color = 0, size = 20f)

    @Test
    fun undoAndRedoWalkBackAndForth() {
        val history = EditHistory().add(a).add(b)
        assertEquals(listOf(a), history.undo().marks)
        assertEquals(listOf(a, b), history.undo().redo().marks)
        assertEquals(emptyList<Mark>(), history.undo().undo().undo().marks)
        assertFalse(EditHistory().canUndo)
    }

    @Test
    fun addingAfterUndoDropsTheRedoStack() {
        val history = EditHistory().add(a).add(b).undo().add(c)
        assertEquals(listOf(a, c), history.marks)
        assertFalse(history.canRedo)
        assertTrue(history.undo().canRedo)
    }

    @Test
    fun labelEditsAndDeletionAreUndoableAndKeepOrder() {
        val history = EditHistory().add(a).add(label).add(b)
        val moved = history.updateLabel(7) { it.copy(position = Offset(50f, 60f), background = true) }
        assertEquals(listOf(a, label.copy(position = Offset(50f, 60f), background = true), b), moved.marks)
        assertEquals(history.marks, moved.undo().marks)

        val removed = moved.removeLabel(7)
        assertEquals(listOf(a, b), removed.marks)
        assertEquals(moved.marks, removed.undo().marks)
    }

    @Test
    fun unchangedCommitIsNotAnUndoStep() {
        val history = EditHistory().add(a)
        assertSame(history, history.updateLabel(99) { it.copy(text = "x") })
    }

    @Test
    fun rectBetweenNormalisesAnyDragDirection() {
        assertEquals(Rect(10f, 20f, 30f, 40f), rectBetween(Offset(30f, 40f), Offset(10f, 20f)))
    }
}
