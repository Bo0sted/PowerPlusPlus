package com.bo0sted.powerplusplus.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ComponentStoreTest {

    private data class Part(override val id: String, override val version: String) : Downloadable {
        override val files = emptyList<RemoteFile>()
    }

    private class Store(root: File, parts: List<Part>) : ComponentStore<Part>(root, parts, "test") {
        override fun finishInstall(component: Part, directory: File) = Unit
    }

    /** A root where [installed] maps each id to the version its marker records. */
    private fun rootWith(installed: Map<String, String>): File =
        Files.createTempDirectory("components").toFile().apply {
            installed.forEach { (id, version) ->
                File(this, id).apply { mkdirs() }.resolve("installed").writeText(version)
            }
        }

    @Test
    fun onlyTheCatalogVersionCountsAsInstalled() {
        val engine = Part("engine", "1.31.0")
        val model = Part("model", "Medium")
        val store = Store(rootWith(mapOf("engine" to "1.30.0", "model" to "Medium")), listOf(engine, model))

        assertFalse(store.isInstalled(engine))
        assertTrue(store.isInstalled(model))
        assertEquals("1.30.0", (store.state.value.getValue("engine") as ComponentState.Outdated).installedVersion)
        assertTrue(store.state.value.getValue("model") is ComponentState.Installed)
    }

    @Test
    fun noMarkerIsNotInstalled() {
        val part = Part("engine", "1.31.0")
        val root = rootWith(emptyMap()).apply { File(this, "engine/engine.aar.part").apply { parentFile!!.mkdirs() }.writeText("partial") }
        val store = Store(root, listOf(part))

        assertFalse(store.isInstalled(part))
        assertEquals(ComponentState.NotInstalled, store.state.value.getValue("engine"))
    }
}
