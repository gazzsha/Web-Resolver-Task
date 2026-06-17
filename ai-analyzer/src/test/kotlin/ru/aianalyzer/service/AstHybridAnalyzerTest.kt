package ru.aianalyzer.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.aianalyzer.ast.AstFact
import ru.aianalyzer.ast.AstMetricsService
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatException
import ru.aianalyzer.validation.SchemaValidator
import ru.sandbox.model.ExecutionStatus
import ru.sandbox.model.SandboxExecutionResult
import java.util.UUID
import java.util.concurrent.TimeUnit

class AstHybridAnalyzerTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val schemaValidator = SchemaValidator(mapper)

    private fun buildGigaChatAnalyzer(client: GigaChatClient): GigaChatAnalyzer {
        val cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(50)
            .build<String, AIAnalysisResult>()
        return GigaChatAnalyzer(client, mapper, SimpleRuleBasedAnalyzer(), cache, schemaValidator)
    }

    private fun successExecResults() = listOf(
        SandboxExecutionResult(
            requestId = UUID.randomUUID(),
            status = ExecutionStatus.SUCCESS,
            output = "42",
            error = null,
            executionTimeMs = 10,
            memoryUsedKb = 1024
        )
    )

    private fun astFactWithLoop() = AstFact(
        language = "python",
        hasLoop = true,
        hasRecursion = false,
        hasComparison = true,
        cyclomaticComplexity = 3,
        methodCount = 1,
        maxNestingDepth = 2,
        suspiciousReturnsConstant = false,
        lineCount = 15
    )

    private fun astFactSuspicious() = AstFact(
        language = "python",
        hasLoop = false,
        hasRecursion = false,
        hasComparison = false,
        cyclomaticComplexity = 1,
        methodCount = 1,
        maxNestingDepth = 1,
        suspiciousReturnsConstant = true,
        lineCount = 3
    )

    // ----------------------------------------------------------------
    // Test 1: happy path — AST facts with loop are passed to LLM,
    //         result is parsed correctly.
    // ----------------------------------------------------------------
    @Test
    fun `happy path - AstFact with loop is forwarded to LLM and result is parsed`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":78,"issues":["slow loop"],"recommendations":["use list comprehension"],"explanation":"Использован цикл for.","complexity":"MEDIUM"}"""

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } returns astFactWithLoop()

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        val result = analyzer.analyze("for i in range(10): print(i)", "python", successExecResults())

        assertEquals(78, result.codeQuality)
        assertEquals("ast-hybrid", result.modelVersion)
        assertTrue(result.explanation.contains("AST-факты"))
        assertTrue(result.explanation.contains("цикл=true"))
        verify(exactly = 1) { client.chatCompletion(any(), any()) }
    }

    // ----------------------------------------------------------------
    // Test 2: cross-check — suspiciousReturnsConstant=true + LLM gives 85
    //         → clamped to 60.
    // ----------------------------------------------------------------
    @Test
    fun `cross-check clamps codeQuality to 60 when suspiciousReturnsConstant=true and LLM says 85`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":85,"issues":[],"recommendations":[],"explanation":"Код выглядит хорошо.","complexity":"LOW"}"""

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } returns astFactSuspicious()

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        val result = analyzer.analyze("def solve(): return 42", "python", successExecResults())

        assertEquals(60, result.codeQuality)
        assertEquals("ast-hybrid", result.modelVersion)
        assertTrue(result.explanation.contains("подозрение"))
    }

    // ----------------------------------------------------------------
    // Test 3: LLM throws → fallback to SimpleRuleBasedAnalyzer,
    //         result still annotated with AST info.
    // ----------------------------------------------------------------
    @Test
    fun `LLM exception falls back to SimpleRuleBasedAnalyzer and annotates result with AST info`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } throws GigaChatException("network timeout")

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } returns astFactWithLoop()

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        val result = analyzer.analyze("for i in range(10): pass", "python", successExecResults())

        // modelVersion must indicate fallback was used
        assertTrue(result.modelVersion.contains("fallback"), "Expected fallback model version, got: ${result.modelVersion}")
        // Explanation must still contain AST annotation
        assertTrue(result.explanation.contains("AST-факты"), "Expected AST annotation in explanation")
    }

    // ----------------------------------------------------------------
    // Test 4: extraContext containing <AST_FACTS> reaches GigaChatClient.chatCompletion.
    // ----------------------------------------------------------------
    @Test
    fun `extraContext with AST_FACTS block is passed verbatim to GigaChatClient chatCompletion`() {
        val capturedUserPrompt = slot<String>()
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), capture(capturedUserPrompt)) } returns
            """{"codeQuality":70,"issues":[],"recommendations":[],"explanation":"ok","complexity":"LOW"}"""

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } returns astFactWithLoop()

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        analyzer.analyze("for i in range(5): pass", "python", successExecResults())

        assertTrue(
            capturedUserPrompt.captured.contains("<AST_FACTS>"),
            "Expected <AST_FACTS> tag in user prompt, got:\n${capturedUserPrompt.captured}"
        )
    }

    // ----------------------------------------------------------------
    // Test 5: suspiciousReturnsConstant=true but quality=50 → NOT clamped.
    // ----------------------------------------------------------------
    @Test
    fun `cross-check does not clamp codeQuality when already at or below 60`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":50,"issues":[],"recommendations":[],"explanation":"Низкое качество.","complexity":"LOW"}"""

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } returns astFactSuspicious()

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        val result = analyzer.analyze("def solve(): return 42", "python", successExecResults())

        assertEquals(50, result.codeQuality)
    }

    // ----------------------------------------------------------------
    // Test 6: AST extraction itself throws → empty AstFact, LLM still runs.
    // ----------------------------------------------------------------
    @Test
    fun `AST extraction failure uses empty AstFact and still calls LLM`() {
        val client = mockk<GigaChatClient>()
        every { client.chatCompletion(any(), any()) } returns
            """{"codeQuality":72,"issues":[],"recommendations":[],"explanation":"ok","complexity":"MEDIUM"}"""

        val astService = mockk<AstMetricsService>()
        every { astService.extract(any(), any()) } throws RuntimeException("parser crashed")

        val analyzer = AstHybridAnalyzer(
            gigaChatAnalyzer = buildGigaChatAnalyzer(client),
            astMetricsService = astService,
            fallback = SimpleRuleBasedAnalyzer()
        )

        val result = analyzer.analyze("x = 1", "python", successExecResults())

        assertEquals(72, result.codeQuality)
        verify(exactly = 1) { client.chatCompletion(any(), any()) }
    }
}
