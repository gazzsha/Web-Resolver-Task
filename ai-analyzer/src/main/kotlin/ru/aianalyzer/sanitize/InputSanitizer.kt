package ru.aianalyzer.sanitize

import java.text.Normalizer
import java.util.Base64

class InputTooLargeException(message: String) : RuntimeException(message)

object InputSanitizer {

    fun normalizeUnicode(s: String): String {
        if (s.isEmpty()) return s
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC)
        return buildString(nfkc.length) {
            for (ch in nfkc) {
                val cp = ch.code
                val isBidi = cp in 0x202A..0x202E || cp in 0x2066..0x2069
                val isZeroWidth = cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF
                // Keep \n (0x0A), \r (0x0D), \t (0x09); strip other C0 (0x00..0x1F) and C1 (0x7F..0x9F)
                val isControl = (cp in 0x00..0x1F && cp != 0x09 && cp != 0x0A && cp != 0x0D) ||
                    cp in 0x7F..0x9F
                if (!isBidi && !isZeroWidth && !isControl) append(ch)
            }
        }
    }

    fun enforceSizeLimit(s: String, maxBytes: Int = 16 * 1024): String {
        val byteCount = s.toByteArray(Charsets.UTF_8).size
        if (byteCount > maxBytes) {
            throw InputTooLargeException("code is $byteCount bytes, limit is $maxBytes")
        }
        return s
    }

    fun spotlightCode(code: String, language: String): String {
        val encoded = Base64.getEncoder().encodeToString(code.toByteArray(Charsets.UTF_8))
        return "<STUDENT_CODE_BASE64 lang=$language>\n$encoded\n</STUDENT_CODE_BASE64>"
    }

    private val htmlTagRegex = Regex("<[^>]+>")
    private val markdownImageRegex = Regex("""!\[([^\]]*)\]\([^)]*\)""")
    private val dangerousScheme = Regex("""(?i)(javascript|data|vbscript|file):""")

    /**
     * Strip HTML/JS/data-URI/markdown-image from LLM output before it reaches the UI.
     * Used to neutralise V5: студент кладёт <script>/onerror/javascript:/![](leak) в код,
     * LLM копирует это в explanation, фронт исполняет.
     */
    fun stripUnsafeOutput(text: String): String =
        text
            .replace(markdownImageRegex, "[image: $1]")
            .replace(htmlTagRegex, "")
            .replace(dangerousScheme, "blocked:")
}
