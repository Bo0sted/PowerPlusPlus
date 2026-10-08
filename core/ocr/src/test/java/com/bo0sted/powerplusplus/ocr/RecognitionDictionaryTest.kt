package com.bo0sted.powerplusplus.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionDictionaryTest {

    @Test
    fun readsPlainAndSingleQuotedEntriesAndStopsAtTheListEnd() {
        val yaml = """
            PostProcess:
              name: CTCLabelDecode
              character_dict:
              - '!'
              - '"'
              - ''''
              - '0'
              - a
              - 中
            Other: x
        """.trimIndent()
        assertEquals(listOf("!", "\"", "'", "0", "a", "中"), RecognitionDictionary.parse(yaml))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAFileWithoutADictionary() {
        RecognitionDictionary.parse("PostProcess:\n  name: CTCLabelDecode\n")
    }
}
