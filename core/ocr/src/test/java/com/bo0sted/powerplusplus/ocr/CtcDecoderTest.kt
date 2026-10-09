package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class CtcDecoderTest {

    private val decoder = CtcDecoder(listOf("a", "b", " "))

    /** One step per class index, with [probability] on that class and the rest spread evenly. */
    private fun steps(vararg best: Int, probability: Float = 0.8f): FloatArray {
        val classes = 4
        val rest = (1f - probability) / (classes - 1)
        return FloatArray(best.size * classes) { i -> if (i % classes == best[i / classes]) probability else rest }
    }

    @Test
    fun collapsesRepeatsAndDropsBlanks() {
        // a a blank a b b blank space → "aab "
        val (text, score) = decoder.decode(steps(1, 1, 0, 1, 2, 2, 0, 3), offset = 0, steps = 8)
        assertEquals("aab ", text)
        assertEquals(0.8f, score, 1e-6f)
    }

    @Test
    fun scoresOnlyTheKeptSteps() {
        val probabilities = steps(1, 0, 2).also { it[4 * 2 + 2] = 0.6f } // The "b" step is less sure.
        val (text, score) = decoder.decode(probabilities, offset = 0, steps = 3)
        assertEquals("ab", text)
        assertEquals(0.7f, score, 1e-6f)
    }

    @Test
    fun allBlanksDecodeToEmptyWithZeroScore() {
        assertEquals("" to 0f, decoder.decode(steps(0, 0), offset = 0, steps = 2))
    }

    @Test
    fun readsFromTheOffset() {
        val batch = steps(1, 2) + steps(2, 1)
        assertEquals("ba", decoder.decode(batch, offset = 8, steps = 2).first)
    }
}
