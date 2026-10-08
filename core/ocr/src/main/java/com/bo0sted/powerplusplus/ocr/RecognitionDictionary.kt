package com.bo0sted.powerplusplus.ocr

/**
 * Reads `PostProcess.character_dict` from PP-OCR's recognition `inference.yml`.
 *
 * Every entry in the pinned file (SHA-256 991b700f…) is a list item that is either one plain
 * character or a single-quoted string, where `''` stands for `'` — checked against PyYAML, which
 * reads 18,708 strings. Anything else is rejected rather than guessed at.
 */
object RecognitionDictionary {

    fun parse(yaml: String): List<String> {
        val lines = yaml.lineSequence().toList()
        val start = lines.indexOfFirst { it.trim() == "character_dict:" }
        require(start >= 0) { "character_dict not found" }
        return lines.drop(start + 1)
            .takeWhile { ITEM.matches(it) }
            .map { unquote(ITEM.find(it)!!.groupValues[1]) }
            .also { require(it.isNotEmpty()) { "character_dict is empty" } }
    }

    private fun unquote(value: String): String = when {
        value.length >= 2 && value.startsWith('\'') && value.endsWith('\'') ->
            value.substring(1, value.length - 1).replace("''", "'")
        value.startsWith('\'') || value.startsWith('"') -> throw IllegalArgumentException("Unsupported scalar: $value")
        else -> value
    }

    private val ITEM = Regex("^\\s+- (.*)$")
}
