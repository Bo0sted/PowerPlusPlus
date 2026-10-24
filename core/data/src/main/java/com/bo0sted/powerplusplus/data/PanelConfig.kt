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
    val volumeShortcuts: VolumeShortcuts = VolumeShortcuts(),
) {
    val cellsPerPage: Int get() = columns * rows

    companion object {
        val COLUMN_RANGE = 2..6
        val ROW_RANGE = 2..8
    }
}

@Serializable
enum class SwipeAxis { Vertical, Horizontal }

/**
 * Modules the volume keys run while the panel is open, as if tapped. A key with nothing assigned
 * changes the volume as usual. Once a key has a double press, its single press waits
 * [doublePressWindowMs] for a second press before it runs.
 */
@Serializable
data class VolumeShortcuts(
    val up: KeyShortcuts = KeyShortcuts(),
    val down: KeyShortcuts = KeyShortcuts(),
    val doublePressWindowMs: Int = DEFAULT_WINDOW_MS,
) {
    val isEmpty: Boolean get() = up.isEmpty && down.isEmpty

    /** Every module type assigned to a key. */
    val typeIds: List<String> get() = listOfNotNull(up.press, up.doublePress, down.press, down.doublePress)

    companion object {
        const val DEFAULT_WINDOW_MS = 300
        val WINDOW_RANGE_MS = 100..1000
    }
}

/** One volume key's modules, by type id; `null` for nothing. */
@Serializable
data class KeyShortcuts(val press: String? = null, val doublePress: String? = null) {
    val isEmpty: Boolean get() = press == null && doublePress == null
}

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
