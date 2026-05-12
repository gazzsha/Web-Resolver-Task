package ru.aianalyzer.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatException
import ru.aianalyzer.validation.SchemaValidator
import ru.sandbox.model.ExecutionStatus
import ru.sandbox.model.SandboxExecutionResult
import java.util.UUID
import java.util.concurrent.TimeUnit

class GigaChatAnalyzerTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val schemaValidator = SchemaValidator(mapper)

    private fun analyzer(client: GigaChatClient): GigaChatAnalyzer {
        val cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(50)
            .build<String, AIAnalysisResult>()
        return GigaChatAnalyzer(client, mapper, SimpleRuleBasedAnalyzer(), cache, schemaValidator)
    }

    private fun execResults(): List<SandboxExecutionResult> = listOf(
        SandboxExecutionResult(
            requestId = UUID.randomUUID(),
            status = ExecutionStatus.SUCCESS,
            output = "ok",
            error = null,
            executionTimeMs = 10,
            memoryUsedKb = 1024
        )
    )

    private fun failedExecResults(): List<SandboxExecutionResult> = listOf(
        SandboxExecutionResult(
            requestId = UUID.randomUUID(),
            status = ExecutionStatus.RUNTIME_ERROR,
            output = "",
            error = "ArrayIndexOutOfBoundsException",
            executionTimeMs = 10,
            memoryUsedKb = 1024
        )
    )

    @Test
    fun `happy path parses GigaChat JSON response`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":85,"issues":["nested loop"],"recommendations":["use HashMap"],"explanation":"Решение работает, но O(n^2).","complexity":"HIGH"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals(85, result.codeQuality)
        assertEquals(1, result.issues.size)
        assertEquals("nested loop", result.issues.first().message)
        assertEquals(listOf("use HashMap"), result.recommendations)
        assertEquals(CodeComplexity.HIGH, result.complexity)
        assertTrue(result.explanation.startsWith("Решение"))
    }

    @Test
    fun `strips markdown json fences`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """```json
{"codeQuality":70,"issues":[],"recommendations":[],"explanation":"ok","complexity":"LOW"}
```"""

        val result = analyzer(client).analyze("code", "python", execResults())

        assertEquals(70, result.codeQuality)
        assertEquals(CodeComplexity.LOW, result.complexity)
    }

    @Test
    fun `5xx fallback returns rule-based result`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } throws GigaChatException("5xx exhausted")

        val result = analyzer(client).analyze("code", "java", execResults())

        assertNotNull(result)
        assertTrue(result.explanation.contains("Passed: 1/1"))
    }

    @Test
    fun `parse error fallback returns rule-based result`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns "this is not JSON at all"

        val result = analyzer(client).analyze("code", "java", execResults())

        assertNotNull(result)
        assertTrue(result.explanation.contains("Passed"))
    }

    @Test
    fun `schema rejects out-of-range codeQuality and falls back after retry`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":150,"issues":[],"recommendations":[],"explanation":"","complexity":"unknown"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals("rule-based", result.modelVersion)
        verify(exactly = 2) { client.chatCompletion(any(), any()) }
    }

    @Test
    fun `schema rejects unknown top-level keys and falls back after retry`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":80,"issues":[],"recommendations":[],"explanation":"ok","complexity":"LOW","verdict":"PASSED"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals("rule-based", result.modelVersion)
        verify(exactly = 2) { client.chatCompletion(any(), any()) }
    }

    @Test
    fun `retry returns valid JSON after first attempt was invalid`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returnsMany listOf(
            """{"codeQuality":150,"issues":[],"recommendations":[],"explanation":"bad","complexity":"LOW"}""",
            """{"codeQuality":75,"issues":[],"recommendations":[],"explanation":"good","complexity":"LOW"}"""
        )

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals(75, result.codeQuality)
        assertEquals("gigachat", result.modelVersion)
        verify(exactly = 2) { client.chatCompletion(any(), any()) }
    }

    @Test
    fun `cache prevents second HTTP call for same code`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":80,"issues":[],"recommendations":[],"explanation":"ok","complexity":"LOW"}"""

        val a = analyzer(client)
        a.analyze("public class A {}", "java", execResults())
        a.analyze("public class A {}", "java", execResults())
        a.analyze("public class A {}", "java", execResults())

        verify(exactly = 1) { client.chatCompletion(any(), any()) }
    }

    @Test
    fun `clamps codeQuality to 60 when sandbox shows failure (V4 cross-check)`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":95,"issues":[],"recommendations":[],"explanation":"perfect","complexity":"LOW"}"""

        val result = analyzer(client).analyze("code", "java", failedExecResults())

        assertEquals(60, result.codeQuality)
    }

    @Test
    fun `does not clamp codeQuality when all tests pass`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":95,"issues":[],"recommendations":[],"explanation":"perfect","complexity":"LOW"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals(95, result.codeQuality)
    }

    @Test
    fun `strips HTML script from explanation (V5 output sanitization)`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":80,"issues":["<b>broken</b>"],"recommendations":["click [here](javascript:alert(1))"],"explanation":"All good <script>alert(1)</script> done","complexity":"LOW"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertFalse(result.explanation.contains("<script>"))
        assertFalse(result.explanation.contains("</script>"))
        assertFalse(result.issues.first().message.contains("<b>"))
        assertFalse(result.recommendations.first().contains("javascript:"))
    }

    @Test
    fun `oversized code triggers fallback without calling chatCompletion`() {
        val client = mockk<GigaChatClient>()
        val oversized = "a".repeat(16385)

        val result = analyzer(client).analyze(oversized, "java", execResults())

        assertNotNull(result)
        verify(exactly = 0) { client.chatCompletion(any(), any()) }
    }
}
