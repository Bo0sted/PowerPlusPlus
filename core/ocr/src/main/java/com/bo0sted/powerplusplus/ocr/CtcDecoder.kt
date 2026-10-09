package com.bo0sted.powerplusplus.ocr

/**
 * Greedy CTC decoding, following PaddleX's `CTCLabelDecode` (`text_recognition/processors.py`,
 * checked 2026-10-08): take the most likely class at each step, collapse repeats, then drop the blank
 * (class 0). Class `i ≥ 1` is `characters[i - 1]`. The score is the mean probability of the kept
 * steps, or 0 if none are kept.
 */
internal class CtcDecoder(private val characters: List<String>) {

    /** Decodes one line from [probabilities], `steps × (characters + 1)` values starting at [offset]. */
    fun decode(probabilities: FloatArray, offset: Int, steps: Int): Pair<String, Float> {
        val classes = characters.size + 1
        val text = StringBuilder()
        var scoreSum = 0.0
        var kept = 0
        var previous = -1
        for (step in 0 until steps) {
            val start = offset + step * classes
            var best = 0
            for (c in 1 until classes) if (probabilities[start + c] > probabilities[start + best]) best = c
            if (best != BLANK && best != previous) {
                text.append(characters[best - 1])
                scoreSum += probabilities[start + best]
                kept++
            }
            previous = best
        }
        return text.toString() to if (kept == 0) 0f else (scoreSum / kept).toFloat()
    }

    private companion object {
        const val BLANK = 0
    }
}
