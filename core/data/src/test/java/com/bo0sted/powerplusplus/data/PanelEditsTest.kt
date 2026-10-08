package com.bo0sted.powerplusplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PanelEditsTest {

    private val base = PanelConfig(columns = 2, rows = 2, pages = listOf(PanelPage(id = "p1"), PanelPage(id = "p2")))

    private fun PanelConfig.page(id: String) = pages.first { it.id == id }

    @Test
    fun addRejectsOccupiedAndOutOfRangeCells() {
        val one = base.withModuleAdded("p1", 0, "a")
        assertSame(one, one.withModuleAdded("p1", 0, "b"))
        assertSame(one, one.withModuleAdded("p1", 4, "b"))
        assertEquals(listOf("a"), one.page("p1").slots.map { it.typeId })
    }

    @Test
    fun moveIntoOccupiedCellSwapsAcrossPages() {
        val config = base.withModuleAdded("p1", 0, "a").withModuleAdded("p2", 3, "b")
        val a = config.page("p1").slots.single()

        val moved = config.withSlotMoved(a.id, "p2", 3)

        assertEquals(listOf("b" to 0), moved.page("p1").slots.map { it.typeId to it.cell })
        assertEquals(listOf("a" to 3), moved.page("p2").slots.map { it.typeId to it.cell })
    }

    @Test
    fun shrinkingGridReflowsWithoutLosingModules() {
        val full = PanelConfig(columns = 3, rows = 2, pages = listOf(PanelPage(id = "p1")))
            .let { c -> (0 until 6).fold(c) { acc, i -> acc.withModuleAdded("p1", i, "m$i") } }

        val shrunk = full.withGrid(columns = 2, rows = 2)

        assertEquals(6, shrunk.pages.sumOf { it.slots.size })
        assertEquals(2, shrunk.pages.size)
        shrunk.pages.forEach { page ->
            assertEquals(page.slots.size, page.slots.map { it.cell }.toSet().size)
            page.slots.forEach { assert(it.cell < shrunk.cellsPerPage) }
        }
    }

    @Test
    fun lastPageCannotBeRemoved() {
        val single = PanelConfig()
        assertSame(single, single.withPageRemoved(single.pages.single().id))
    }
}
