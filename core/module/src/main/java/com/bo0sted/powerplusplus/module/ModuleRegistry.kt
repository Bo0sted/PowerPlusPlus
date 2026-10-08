package com.bo0sted.powerplusplus.module

/** Lookup of every [PanelModule] contributed to the app, keyed by [PanelModule.typeId]. */
class ModuleRegistry(modules: Set<PanelModule>) {
    private val byType: Map<String, PanelModule> = modules.associateBy { it.typeId }

    init {
        require(byType.size == modules.size) { "Duplicate module typeId in ${modules.map { it.typeId }}" }
    }

    val all: List<PanelModule> = modules.sortedBy { it.typeId }

    operator fun get(typeId: String): PanelModule? = byType[typeId]
}
