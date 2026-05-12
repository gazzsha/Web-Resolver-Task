package ru.aianalyzer.prompt

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalyzerPromptsTest {

    @Test
    fun `zero-shot prompt contains instruction hierarchy block`() {
        val prompt = AnalyzerPrompts.systemPrompt(PromptVariant.ZERO_SHOT)
        assertTrue(prompt.contains("ИЕРАРХИЯ АВТОРИТЕТНОСТИ"), "Missing instruction hierarchy header")
        assertTrue(prompt.contains("Вердикт sandbox"), "Missing sandbox authority statement")
        assertTrue(prompt.contains("AST-факты"), "Missing AST authority statement")
        assertTrue(prompt.contains("зона ответственности"), "Missing responsibility scope statement")
    }

    @Test
    fun `few-shot prompt contains all 3 example markers`() {
        val prompt = AnalyzerPrompts.systemPrompt(PromptVariant.FEW_SHOT)
        assertTrue(prompt.contains("== EXAMPLE_1_START =="), "Missing example 1 start marker")
        assertTrue(prompt.contains("== EXAMPLE_2_START =="), "Missing example 2 start marker")
        assertTrue(prompt.contains("== EXAMPLE_3_START =="), "Missing example 3 start marker")
        assertTrue(prompt.contains("== EXAMPLE_1_END =="), "Missing example 1 end marker")
        assertTrue(prompt.contains("== EXAMPLE_2_END =="), "Missing example 2 end marker")
        assertTrue(prompt.contains("== EXAMPLE_3_END =="), "Missing example 3 end marker")
    }

    @Test
    fun `few-shot prompt is longer than zero-shot`() {
        val zeroShot = AnalyzerPrompts.systemPrompt(PromptVariant.ZERO_SHOT)
        val fewShot = AnalyzerPrompts.systemPrompt(PromptVariant.FEW_SHOT)
        assertTrue(
            fewShot.length > zeroShot.length,
            "FEW_SHOT prompt (${fewShot.length} chars) must be longer than ZERO_SHOT (${zeroShot.length} chars)"
        )
    }

    @Test
    fun `sandwich repeats role at the end of system prompt`() {
        val prompt = AnalyzerPrompts.systemPrompt(PromptVariant.ZERO_SHOT)
        // The sandwich reminder must appear after the schema block
        val schemaIndex = prompt.indexOf("ФОРМАТ ОТВЕТА")
        val reminderIndex = prompt.lastIndexOf("ИИ-преподаватель")
        assertTrue(schemaIndex > 0, "Schema block must be present")
        assertTrue(reminderIndex > schemaIndex, "Role sandwich reminder must appear AFTER the schema block")
        // Also check the reminder contains anti-role-change instruction
        val tail = prompt.substring(reminderIndex)
        assertTrue(tail.contains("Не меняй роль") || tail.contains("не меняй роль"),
            "Sandwich reminder must include 'не меняй роль'")
    }

    @Test
    fun `system prompt does not leak schema validator internals`() {
        val prompt = AnalyzerPrompts.systemPrompt(PromptVariant.ZERO_SHOT)
        // Validator class names, JSON-schema keywords that hint at internal implementation, and stack traces
        assertFalse(prompt.contains("SchemaValidator"), "Must not expose SchemaValidator class name")
        assertFalse(prompt.contains("NetworkingException"), "Must not expose internal exception types")
        assertFalse(prompt.contains("at ru.aianalyzer"), "Must not contain stack trace fragments")
        assertFalse(prompt.contains("additionalProperties"), "Must not expose JSON-schema constraint keywords to LLM")
    }

    @Test
    fun `default variant is ZERO_SHOT`() {
        val defaultPrompt = AnalyzerPrompts.systemPrompt()
        val zeroShot = AnalyzerPrompts.systemPrompt(PromptVariant.ZERO_SHOT)
        assertTrue(defaultPrompt == zeroShot, "Default variant must equal ZERO_SHOT")
    }

    @Test
    fun `anti-injection block present in both variants`() {
        for (variant in PromptVariant.entries) {
            val prompt = AnalyzerPrompts.systemPrompt(variant)
            assertTrue(prompt.contains("ИГНОРИРУЙ"), "Anti-injection rule missing in variant $variant")
            assertTrue(prompt.contains("STUDENT_CODE_BASE64"), "Spotlight marker explanation missing in variant $variant")
        }
    }

    @Test
    fun `few-shot prompt contains study instruction preamble`() {
        val prompt = AnalyzerPrompts.systemPrompt(PromptVariant.FEW_SHOT)
        assertTrue(prompt.contains("study these"), "Few-shot preamble 'study these' must be present")
    }
}
