package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.unit.IntRect

/**
 * Where each word of [text] is, for word selection. Words are separated by spaces only, so dashed
 * runs such as `well-known` or `555-123-4567` stay whole. Punctuation around a word is left out
 * (`"yummy,"` → `yummy`, `(maybe)` → `maybe`). A run of nothing but punctuation, or a lone separator
 * such as the `-` in `Home - Settings`, isn't a word.
 */
fun wordRanges(text: String): List<IntRange> =
    WORD.findAll(text).mapNotNull { match ->
        var first = match.range.first
        var last = match.range.last
        while (first <= last && text[first] in SURROUNDING_PUNCTUATION) first++
        while (last >= first && text[last] in SURROUNDING_PUNCTUATION) last--
        (first..last).takeUnless { range -> range.isEmpty() || range.all { text[it] in SEPARATORS } }
    }.toList()

/**
 * The words of [text] with their bounds, from the app's own character positions: [characters] holds
 * one entry per character of [text], `null` where the app gave none (off screen, or past what it
 * reported). Each word's bounds are the union of its characters', kept inside [clip] (the block's
 * visible bounds); a word with nothing inside is left out.
 *
 * Returns `null` when no character has a position at all, so the caller can estimate instead.
 */
fun wordsFromCharacters(text: String, characters: List<IntRect?>, clip: IntRect): List<TextBlock>? {
    if (characters.none { it != null }) return null
    return wordRanges(text).mapNotNull { range ->
        range.mapNotNull { characters.getOrNull(it) }
            .reduceOrNull { union, next -> union.union(next) }
            ?.intersect(clip)
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { TextBlock(text.substring(range), it) }
    }
}

private val WORD = Regex("""\S+""")

/** Characters that, standing alone between spaces, separate text rather than being part of it. */
private const val SEPARATORS = "-–—•·|/\\"

/** Sentence punctuation, quotes and brackets; dashes, `#`, `@`, `$`, `%` and the like stay with the word. */
private const val SURROUNDING_PUNCTUATION = ".,;:!?\"'“”‘’«»()[]{}…"

private fun IntRect.union(other: IntRect) =
    IntRect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
