package com.bo0sted.powerplusplus.data

/*
 * Pure edits on a PanelConfig. Each returns a new config; invalid requests return the input unchanged
 * so a stale UI action (for example, a double tap) can never corrupt the layout.
 */

fun PanelConfig.withModuleAdded(pageId: String, cell: Int, typeId: String): PanelConfig {
    val page = pages.firstOrNull { it.id == pageId } ?: return this
    if (cell !in 0 until cellsPerPage || page.slots.any { it.cell == cell }) return this
    return mapPage(pageId) { it.copy(slots = it.slots + ModuleSlot(typeId = typeId, cell = cell)) }
}

fun PanelConfig.withSlotRemoved(slotId: String): PanelConfig =
    copy(pages = pages.map { page -> page.copy(slots = page.slots.filterNot { it.id == slotId }) })

fun PanelConfig.withSlotEnabled(slotId: String, enabled: Boolean): PanelConfig =
    mapSlot(slotId) { it.copy(enabled = enabled) }

/** Moves a slot to [toCell] on [toPageId]. If another slot is already there, the two swap places. */
fun PanelConfig.withSlotMoved(slotId: String, toPageId: String, toCell: Int): PanelConfig {
    if (toCell !in 0 until cellsPerPage) return this
    val fromPage = pages.firstOrNull { page -> page.slots.any { it.id == slotId } } ?: return this
    val moving = fromPage.slots.first { it.id == slotId }
    val toPage = pages.firstOrNull { it.id == toPageId } ?: return this
    val occupant = toPage.slots.firstOrNull { it.cell == toCell && it.id != slotId }

    return copy(
        pages = pages.map { page ->
            var slots = page.slots
            if (page.id == fromPage.id) {
                slots = slots.filterNot { it.id == slotId }
                if (occupant != null) slots = slots + occupant.copy(cell = moving.cell)
            }
            if (page.id == toPage.id) {
                if (occupant != null) slots = slots.filterNot { it.id == occupant.id }
                slots = slots + moving.copy(cell = toCell)
            }
            page.copy(slots = slots)
        },
    )
}

fun PanelConfig.withPageAdded(): PanelConfig = copy(pages = pages + PanelPage())

/** Removes a page and the modules on it. The last remaining page can't be removed. */
fun PanelConfig.withPageRemoved(pageId: String): PanelConfig {
    if (pages.size <= 1) return this
    return copy(pages = pages.filterNot { it.id == pageId })
}

/**
 * Resizes the grid. Slots that no longer fit are moved to the first free cells, first on their own
 * page, then on later pages; new pages are appended when everything is full. Nothing is ever dropped.
 */
fun PanelConfig.withGrid(columns: Int, rows: Int): PanelConfig {
    val resized = copy(
        columns = columns.coerceIn(PanelConfig.COLUMN_RANGE),
        rows = rows.coerceIn(PanelConfig.ROW_RANGE),
    )
    val capacity = resized.cellsPerPage
    val overflow = ArrayDeque<ModuleSlot>()
    val pages = resized.pages.map { page ->
        val (fit, out) = page.slots.partition { it.cell < capacity }
        overflow += out.sortedBy { it.cell }
        page.copy(slots = fit)
    }.toMutableList()

    var index = 0
    while (overflow.isNotEmpty()) {
        if (index == pages.size) pages += PanelPage()
        val page = pages[index]
        val used = page.slots.mapTo(HashSet()) { it.cell }
        val placed = (0 until capacity).filterNot { it in used }.zip(overflow) { cell, slot -> slot.copy(cell = cell) }
        repeat(placed.size) { overflow.removeFirst() }
        pages[index] = page.copy(slots = page.slots + placed)
        index++
    }
    return resized.copy(pages = pages)
}

private inline fun PanelConfig.mapPage(pageId: String, transform: (PanelPage) -> PanelPage): PanelConfig =
    copy(pages = pages.map { if (it.id == pageId) transform(it) else it })

private inline fun PanelConfig.mapSlot(slotId: String, crossinline transform: (ModuleSlot) -> ModuleSlot): PanelConfig =
    copy(pages = pages.map { page -> page.copy(slots = page.slots.map { if (it.id == slotId) transform(it) else it }) })
