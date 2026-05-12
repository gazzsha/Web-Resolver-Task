package ru.aianalyzer.sanitize

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class InputSanitizerTest {

    @Test
    fun `NFKC normalizes full-width latin to ASCII`() {
        // ｐｕｂｌｉｃ in full-width Unicode -> plain "public"
        val fullWidth = "ｐｕｂｌｉｃ"
        assertEquals("public", InputSanitizer.normalizeUnicode(fullWidth))
    }

    @Test
    fun `bidi RLO control U+202E is removed`() {
        val withRlo = "hello‮world"
        val result = InputSanitizer.normalizeUnicode(withRlo)
        assertFalse(result.contains('‮'))
        assertEquals("helloworld", result)
    }

    @Test
    fun `zero-width U+200B is removed`() {
        val withZw = "pub​lic"
        assertEquals("public", InputSanitizer.normalizeUnicode(withZw))
    }

    @Test
    fun `zero-width U+200C and U+200D are removed`() {
        val input = "a‌b‍c"
        assertEquals("abc", InputSanitizer.normalizeUnicode(input))
    }

    @Test
    fun `BOM U+FEFF is removed`() {
        val withBom = "﻿public"
        assertEquals("public", InputSanitizer.normalizeUnicode(withBom))
    }

    @Test
    fun `C0 control U+0001 through U+0008 are removed but newline and tab are kept`() {
        val input = "hello\n\tworld"
        val result = InputSanitizer.normalizeUnicode(input)
        assertEquals("hello\n\tworld", result)
    }

    @Test
    fun `combining diacritics are composed by NFKC`() {
        // 'е' + combining acute accent (U+0301) -> one char after NFKC (implementation-dependent, but must not expand)
        val composed = InputSanitizer.normalizeUnicode("é")
        // NFKC composes e + combining acute -> é (U+00E9) as a single char
        assertEquals(1, composed.length)
        assertEquals('é', composed[0])
    }

    @Test
    fun `enforceSizeLimit passes exactly 16384 bytes`() {
        val s = "a".repeat(16384)
        assertEquals(s, InputSanitizer.enforceSizeLimit(s))
    }

    @Test
    fun `enforceSizeLimit throws InputTooLargeException for 16385 bytes`() {
        val s = "a".repeat(16385)
        val ex = assertThrows(InputTooLargeException::class.java) {
            InputSanitizer.enforceSizeLimit(s)
        }
        assertTrue(ex.message!!.contains("16385"))
        assertTrue(ex.message!!.contains("16384"))
    }

    @Test
    fun `spotlightCode body is valid base64 of original code`() {
        val code = "public class A {}"
        val result = InputSanitizer.spotlightCode(code, "java")
        val lines = result.lines()
        val encoded = lines[1]
        val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        assertEquals(code, decoded)
    }

    @Test
    fun `spotlightCode includes lang attribute`() {
        val result = InputSanitizer.spotlightCode("x", "kotlin")
        assertTrue(result.startsWith("<STUDENT_CODE_BASE64 lang=kotlin>"))
        assertTrue(result.endsWith("</STUDENT_CODE_BASE64>"))
    }

    @Test
    fun `normalizeUnicode on empty string returns empty string`() {
        assertEquals("", InputSanitizer.normalizeUnicode(""))
    }

    @Test
    fun `stripUnsafeOutput removes script tags`() {
        val input = "All good <script>alert(1)</script> done"
        val result = InputSanitizer.stripUnsafeOutput(input)
        assertFalse(result.contains("<script>"))
        assertFalse(result.contains("</script>"))
        assertTrue(result.contains("All good"))
    }

    @Test
    fun `stripUnsafeOutput rewrites javascript scheme`() {
        val input = "click [here](javascript:alert(1))"
        val result = InputSanitizer.stripUnsafeOutput(input)
        assertFalse(result.contains("javascript:"))
        assertTrue(result.contains("blocked:"))
    }

    @Test
    fun `stripUnsafeOutput converts markdown image to placeholder`() {
        val input = "before ![pwn](https://attacker.example/log) after"
        val result = InputSanitizer.stripUnsafeOutput(input)
        assertFalse(result.contains("attacker.example"))
        assertTrue(result.contains("[image: pwn]"))
    }

    @Test
    fun `stripUnsafeOutput keeps plain text untouched`() {
        val input = "Решение работает, но имеет O(n^2) сложность."
        assertEquals(input, InputSanitizer.stripUnsafeOutput(input))
    }

    @Test
    fun `normalizeUnicode is idempotent`() {
        val input = "ｐ‮public​\n"
        val once = InputSanitizer.normalizeUnicode(input)
        val twice = InputSanitizer.normalizeUnicode(once)
        assertEquals(once, twice)
    }
}
