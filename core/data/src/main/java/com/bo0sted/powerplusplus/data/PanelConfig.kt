package com.bo0sted.powerplusplus.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Everything the user has configured about the power panel. Persisted as JSON. */
@Serializable
data class PanelConfig(
    val columns: Int = 4,
    val rows: Int = 5,
    val swipeAxis: SwipeAxis = SwipeAxis.Vertical,
    val backdrop: Backdrop = Backdrop.Blur,
    val pages: List<PanelPage> = listOf(PanelPage()),
) {
    val cellsPerPage: Int get() = columns * rows

    companion object {
        val COLUMN_RANGE = 2..6
        val ROW_RANGE = 2..8
    }
}

@Serializable
enum class SwipeAxis { Vertical, Horizontal }

/** What sits behind the panel. [Blur] falls back to [Dim] whenever the device can't blur. */
@Serializable
enum class Backdrop { Blur, Dim }

@Serializable
data class PanelPage(
    val id: String = newId(),
    val slots: List<ModuleSlot> = emptyList(),
)

/**
 * One module placed on a page. [cell] is the row-major index within the page's grid.
 * Multi-cell modules would add a span here; v1 is single-cell only.
 */
@Serializable
data class ModuleSlot(
    val id: String = newId(),
    val typeId: String,
    val cell: Int,
    val enabled: Boolean = true,
)

internal fun newId(): String = UUID.randomUUID().toString()
