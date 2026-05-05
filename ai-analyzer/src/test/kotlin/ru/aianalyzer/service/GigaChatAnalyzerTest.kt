package ru.aianalyzer.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatException
import ru.sandbox.model.ExecutionStatus
import ru.sandbox.model.SandboxExecutionResult
import java.util.UUID
import java.util.concurrent.TimeUnit

class GigaChatAnalyzerTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun analyzer(client: GigaChatClient): GigaChatAnalyzer {
        val cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(50)
            .build<String, AIAnalysisResult>()
        return GigaChatAnalyzer(client, mapper, SimpleRuleBasedAnalyzer(), cache)
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
    fun `coerces codeQuality outside 0_100`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":150,"issues":[],"recommendations":[],"explanation":"","complexity":"unknown"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals(100, result.codeQuality)
        assertEquals(CodeComplexity.MEDIUM, result.complexity)
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
}
