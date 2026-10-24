package com.bo0sted.powerplusplus.textpick

/** One picked block: its index in the block list and, if the user changed it, the new text. */
data class Pick(val index: Int, val edited: String? = null)

/**
 * The blocks the user picked, in the order they picked them, with their edits. Immutable: every
 * change returns a new list.
 */
data class PickList(val picks: List<Pick> = emptyList()) {

    val size: Int get() = picks.size

    operator fun contains(index: Int): Boolean = picks.any { it.index == index }

    fun isEdited(index: Int): Boolean = picks.any { it.index == index && it.edited != null }

    /** Adds [index] at the end, or removes it if it's already picked. */
    fun toggled(index: Int): PickList =
        if (index in this) PickList(picks.filterNot { it.index == index }) else PickList(picks + Pick(index))

    /** Adds every block that isn't picked yet, in [count] order, after the current picks. */
    fun withAll(count: Int): PickList = PickList(picks + (0 until count).filterNot { it in this }.map { Pick(it) })

    /** Sets [index]'s text; text equal to the [original] clears the edit. */
    fun edited(index: Int, text: String, original: String): PickList =
        PickList(picks.map { if (it.index == index) it.copy(edited = text.takeIf { t -> t != original }) else it })

    /** The text of [pick]: its edit, or the block's own text. */
    fun textOf(pick: Pick, blocks: List<TextBlock>): String = pick.edited ?: blocks[pick.index].text

    /** Every pick's text, in pick order, one per line. */
    fun joinedText(blocks: List<TextBlock>): String = picks.joinToString("\n") { textOf(it, blocks) }

    /**
     * The picks as lines, where [lineOf] says which line each index belongs to (for words, the block
     * it's from). Lines come in the order they were first picked; within a line, picks are in index
     * order (the text's own order) and joined by spaces.
     */
    fun joinedLines(blocks: List<TextBlock>, lineOf: (Int) -> Int): String =
        picks.groupBy { lineOf(it.index) }.values.joinToString("\n") { line ->
            line.sortedBy { it.index }.joinToString(" ") { textOf(it, blocks) }
        }
}
