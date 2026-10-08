package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VerifiedDownloaderTest {

    @Test
    fun sha256MatchesTheKnownDigest() {
        val file = File(Files.createTempDirectory("ocr").toFile(), "abc").apply { writeText("abc") }
        // SHA-256("abc") from FIPS 180-2.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", VerifiedDownloader.sha256(file))
    }
}
