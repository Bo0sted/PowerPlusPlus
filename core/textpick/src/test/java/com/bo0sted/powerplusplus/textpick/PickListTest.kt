package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickListTest {

    private val blocks = listOf("Alpha", "Beta", "Gamma").map { TextBlock(it, IntRect(0, 0, 10, 10)) }

    @Test
    fun keepsPickOrderAndTogglesOff() {
        val list = PickList().toggled(2).toggled(0)
        assertEquals("Gamma\nAlpha", list.joinedText(blocks))
        assertEquals("Gamma", list.toggled(0).joinedText(blocks))
    }

    @Test
    fun selectAllAppendsTheRestInOrder() {
        assertEquals("Beta\nAlpha\nGamma", PickList().toggled(1).withAll(3).joinedText(blocks))
    }

    @Test
    fun editsAreCopiedAndTrackedUntilRestored() {
        val edited = PickList().toggled(0).toggled(1).edited(1, "Bet a", original = "Beta")
        assertEquals("Alpha\nBet a", edited.joinedText(blocks))
        assertTrue(edited.isEdited(1))
        assertFalse(edited.isEdited(0))
        assertFalse(edited.edited(1, "Beta", original = "Beta").isEdited(1))
    }

    @Test
    fun removingAnEditedPickDropsTheEdit() {
        val list = PickList().toggled(1).edited(1, "x", original = "Beta").toggled(1).toggled(1)
        assertEquals("Beta", list.joinedText(blocks))
    }

    @Test
    fun wordsJoinIntoOneLinePerBlockInFirstPickOrder() {
        // Two boxes: "green apples are yummy 555-123-4567" and "red apples are also yummy".
        val words = listOf("green", "apples", "are", "yummy", "555-123-4567", "red", "apples", "are", "also", "yummy")
            .map { TextBlock(it, IntRect(0, 0, 10, 10)) }
        val blockOf = { index: Int -> if (index < 5) 0 else 1 }
        val picks = PickList().toggled(4).toggled(9).toggled(0)
        assertEquals("green 555-123-4567\nyummy", picks.joinedLines(words, blockOf))
        assertEquals("yummy\ngreen 555-123-4567", PickList().toggled(9).toggled(4).toggled(0).joinedLines(words, blockOf))
        assertEquals("green 555-000-0000", picks.toggled(9).edited(4, "555-000-0000", "555-123-4567").joinedLines(words, blockOf))
    }
}
