package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextWordsTest {

    private fun words(text: String) = wordRanges(text).map { text.substring(it) }

    @Test
    fun splitsOnSpacesOnlySoDashedRunsStayWhole() {
        assertEquals(listOf("green", "apples", "are", "yummy", "555-123-4567"), words("green apples are yummy 555-123-4567"))
        assertEquals(listOf("a", "well-known", "tree"), words("  a well-known\ttree\n"))
    }

    @Test
    fun leavesOutSurroundingPunctuationButKeepsSymbols() {
        assertEquals(listOf("Yes", "maybe", "it's", "#tag", "@user", "$5", "100%"), words("“Yes,” (maybe) it's #tag @user $5 100%."))
        assertEquals(listOf("Home", "Settings", "re-do"), words("Home - Settings • | re-do"))
        assertEquals(emptyList<String>(), words("… !!"))
    }

    @Test
    fun wordBoundsAreTheirCharactersUnionClipped() {
        // "ab cd": one 10 px cell per character; "d" is off screen.
        val cells = listOf(IntRect(0, 0, 10, 20), IntRect(10, 0, 20, 20), IntRect(20, 0, 30, 20), IntRect(30, 0, 40, 20), null)
        val clip = IntRect(0, 0, 35, 20)
        assertEquals(
            listOf(TextBlock("ab", IntRect(0, 0, 20, 20)), TextBlock("cd", IntRect(30, 0, 35, 20))),
            wordsFromCharacters("ab cd", cells, clip),
        )
    }

    @Test
    fun wordsWithNoPlacedCharacterAreLeftOutAndNoPositionsAtAllMeansEstimate() {
        val text = "ab cd"
        assertEquals(listOf(TextBlock("ab", IntRect(0, 0, 20, 20))), wordsFromCharacters(text, listOf(IntRect(0, 0, 10, 20), IntRect(10, 0, 20, 20)), IntRect(0, 0, 99, 99)))
        assertNull(wordsFromCharacters(text, List(5) { null }, IntRect(0, 0, 99, 99)))
    }
}
