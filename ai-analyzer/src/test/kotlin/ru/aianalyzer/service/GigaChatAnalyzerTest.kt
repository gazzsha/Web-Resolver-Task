package ru.aianalyzer.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import ru.aianalyzer.client.GigaChatAnalysisPayload
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatException
import ru.aianalyzer.prompt.PromptVariant
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
    fun `fallback writes WARN log mentioning rule-based and produces non-empty result (P1-7)`() {
        // P1-7: при падении GigaChat-вызова логируем WARN и возвращаем непустой rule-based ответ.
        val logger = LoggerFactory.getLogger("ru.aianalyzer.service.GigaChatAnalyzer") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val client = mockk<GigaChatClient>()
            every { client.chatCompletion(any(), any()) } throws GigaChatException("network down")

            val result = analyzer(client).analyze("public class Solution {}", "java", failedExecResults())

            assertEquals("rule-based", result.modelVersion)
            assertTrue(result.explanation.isNotBlank())
            assertNotNull(result.complexity)

            val warns = appender.list.filter { it.level == Level.WARN }
            assertTrue(
                warns.any { it.formattedMessage.contains("falling back to rule-based") },
                "Ожидаем WARN 'falling back to rule-based', фактические логи: ${warns.map { it.formattedMessage }}"
            )
        } finally {
            logger.detachAppender(appender)
        }
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
    fun `schema accepts unknown top-level keys (additionalProperties=true)`() {
        // After the real GigaChat run we relaxed additionalProperties to true —
        // the model often augments the JSON with extra keys (e.g. "verdict") and
        // strict mode caused 100% schema-reject. Required fields and value ranges
        // still hold; extras are silently dropped by Jackson.
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":80,"issues":[],"recommendations":[],"explanation":"ok","complexity":"LOW","verdict":"PASSED"}"""

        val result = analyzer(client).analyze("code", "java", execResults())

        assertEquals(80, result.codeQuality)
        assertEquals("gigachat", result.modelVersion)
        verify(exactly = 1) { client.chatCompletion(any(), any()) }
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
    fun `clamps codeQuality to 20 when ALL tests fail (dynamic cap 0 of N)`() {
        // passed=0/total=1 → динамический cap = 20 (было 40 до введения пропорциональной шкалы).
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":95,"issues":[],"recommendations":[],"explanation":"perfect","complexity":"LOW"}"""

        val result = analyzer(client).analyze("code", "java", failedExecResults())

        assertEquals(20, result.codeQuality)
    }

    @Test
    fun `clamps codeQuality to 70 when N-1 of N tests pass (dynamic cap N-1 of N)`() {
        // passed=1/total=2 → N-1/N → динамический cap = 70 (было 60 за любой частичный провал).
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":95,"issues":[],"recommendations":[],"explanation":"perfect","complexity":"LOW"}"""

        val mixed = execResults() + failedExecResults()
        val result = analyzer(client).analyze("code", "java", mixed)

        assertEquals(70, result.codeQuality)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Dynamic cap tests (proportional to passed/total).
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class MapPayloadCap {

        /** Строит analyzer с указанным [disableVerdictGuards]. */
        private fun analyzerWithFlags(
            client: GigaChatClient,
            disableVerdictGuards: Boolean = false
        ): GigaChatAnalyzer {
            val cache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(50)
                .build<String, AIAnalysisResult>()
            return GigaChatAnalyzer(
                client = client,
                objectMapper = mapper,
                fallback = SimpleRuleBasedAnalyzer(),
                cache = cache,
                schemaValidator = schemaValidator,
                promptVariant = PromptVariant.ZERO_SHOT,
                disableVerdictGuards = disableVerdictGuards
            )
        }

        /** Генерирует [count] результатов с SUCCESS-статусом. */
        private fun successResults(count: Int) = List(count) {
            SandboxExecutionResult(
                requestId = UUID.randomUUID(),
                status = ExecutionStatus.SUCCESS,
                output = "ok",
                error = null,
                executionTimeMs = 10,
                memoryUsedKb = 512
            )
        }

        /** Генерирует [count] результатов с RUNTIME_ERROR-статусом. */
        private fun failedResults(count: Int) = List(count) {
            SandboxExecutionResult(
                requestId = UUID.randomUUID(),
                status = ExecutionStatus.RUNTIME_ERROR,
                output = "",
                error = "error",
                executionTimeMs = 10,
                memoryUsedKb = 512
            )
        }

        private fun llmReturning(quality: Int): GigaChatClient {
            val client = mockk<GigaChatClient>()
            every { client.chatCompletion(any(), any()) } returns
                """{"codeQuality":$quality,"issues":[],"recommendations":[],"explanation":"test","complexity":"LOW"}"""
            return client
        }

        @Test
        fun `dynamicCap returns 20 when zero tests pass`() {
            // 0/5 → cap=20; LLM=80 → clamp to 20
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", failedResults(5))

            assertEquals(20, result.codeQuality)
        }

        @Test
        fun `dynamicCap returns 30 when one test passes out of many`() {
            // 1/5 → cap=30; LLM=80 → clamp to 30
            val results = successResults(1) + failedResults(4)
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", results)

            assertEquals(30, result.codeQuality)
        }

        @Test
        fun `dynamicCap returns 50 at half threshold with 2 passed of 5`() {
            // 2/5 → passed*2=4 <= 5 → cap=50; LLM=80 → clamp to 50
            val results = successResults(2) + failedResults(3)
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", results)

            assertEquals(50, result.codeQuality)
        }

        @Test
        fun `dynamicCap returns 50 at half threshold with 3 passed of 6`() {
            // 3/6 → passed*2=6 <= 6 → cap=50; LLM=80 → clamp to 50
            val results = successResults(3) + failedResults(3)
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", results)

            assertEquals(50, result.codeQuality)
        }

        @Test
        fun `dynamicCap is permissive at all-but-one passed`() {
            // 4/5 → passed == total-1 → cap=70; LLM=80 → clamp to 70
            val results = successResults(4) + failedResults(1)
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", results)

            assertEquals(70, result.codeQuality)
        }

        @Test
        fun `dynamicCap is MAX_VALUE on all passed`() {
            // 5/5 → no cap; LLM=95 → preserved
            val result = analyzerWithFlags(llmReturning(95))
                .analyze("code", "java", successResults(5))

            assertEquals(95, result.codeQuality)
        }

        @Test
        fun `dynamicCap is MAX_VALUE when total is zero`() {
            // no execution results → no cap; LLM=90 → preserved
            val result = analyzerWithFlags(llmReturning(90))
                .analyze("code", "java", emptyList())

            assertEquals(90, result.codeQuality)
        }

        @Test
        fun `mapPayload clamps LLM=80 to 20 when 0 of 5 passed`() {
            // Explicit check: cap is upper bound, LLM above cap → clamped
            val result = analyzerWithFlags(llmReturning(80))
                .analyze("code", "java", failedResults(5))

            assertEquals(20, result.codeQuality)
        }

        @Test
        fun `mapPayload preserves LLM=15 when 0 of 5 passed — cap is upper bound not floor`() {
            // Cap=20 is upper bound; LLM=15 is below cap → preserved unchanged
            val result = analyzerWithFlags(llmReturning(15))
                .analyze("code", "java", failedResults(5))

            assertEquals(15, result.codeQuality)
        }

        @Test
        fun `mapPayload returns rawQuality when disableVerdictGuards=true`() {
            // B3 experiment mode: guards bypassed entirely, LLM=85 on 0/5 → 85
            val result = analyzerWithFlags(llmReturning(85), disableVerdictGuards = true)
                .analyze("code", "java", failedResults(5))

            assertEquals(85, result.codeQuality)
            assertEquals("gigachat-no-guards", result.modelVersion)
        }

        @Test
        fun `mapPayload applies extreme cap=10 when suspiciousReturnsConstant and allFailed`() {
            // astSuspiciousReturnsConstant=true + passed=0/total=5 → cap=10; LLM=80 → clamp to 10.
            // mapPayload is internal, so we can call it directly from the same module's test scope.
            val cache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(50)
                .build<String, AIAnalysisResult>()
            val gigaChatAnalyzer = GigaChatAnalyzer(
                client = llmReturning(80),
                objectMapper = mapper,
                fallback = SimpleRuleBasedAnalyzer(),
                cache = cache,
                schemaValidator = schemaValidator
            )
            val payload = ru.aianalyzer.client.GigaChatAnalysisPayload(
                codeQuality = 80,
                issues = emptyList(),
                recommendations = emptyList(),
                explanation = "stub",
                complexity = "LOW"
            )

            val result = gigaChatAnalyzer.mapPayload(
                payload = payload,
                executionResults = failedResults(5),
                astSuspiciousReturnsConstant = true
            )

            assertEquals(10, result.codeQuality)
        }
    }
}
