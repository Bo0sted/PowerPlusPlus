package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCatalogTest {

    @Test
    fun downloadedEngineMatchesTheBundledJavaClassesAndGlue() {
        // Bumping onnxruntime in libs.versions.toml without re-pinning the engine fails here.
        assertEquals(BuildConfig.ORT_VERSION, OcrCatalog.ENGINE.version)
        assertTrue(OcrCatalog.ENGINE.files.single().urls.all { "/${BuildConfig.ORT_VERSION}/" in it })
    }

    @Test
    fun everyFileIsPinnedAndServedOverHttps() {
        OcrCatalog.ALL.flatMap { it.files }.forEach { file ->
            assertTrue(file.name, Regex("[0-9a-f]{64}").matches(file.sha256))
            assertTrue(file.name, file.sizeBytes > 0 && file.urls.isNotEmpty())
            assertTrue(file.name, file.urls.all { it.startsWith("https://") })
        }
        assertEquals(OcrCatalog.ALL.size, OcrCatalog.ALL.map { it.id }.toSet().size)
    }
}
