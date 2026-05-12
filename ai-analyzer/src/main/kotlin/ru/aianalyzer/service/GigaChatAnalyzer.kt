package ru.aianalyzer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.aianalyzer.client.GigaChatAnalysisPayload
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.prompt.AnalyzerPrompts
import ru.aianalyzer.sanitize.InputSanitizer
import ru.aianalyzer.sanitize.InputTooLargeException
import ru.sandbox.model.ExecutionStatus
import ru.sandbox.model.SandboxExecutionResult
import java.security.MessageDigest

/**
 * If sandbox already proved the code is broken, LLM may not raise codeQuality above this.
 * Защита от V4 (JSON-injection «codeQuality:100» при провальных тестах).
 */
private const val FAILED_CODE_QUALITY_CAP = 60

private val logger = KotlinLogging.logger {}

class GigaChatAnalyzer(
    private val client: GigaChatClient,
    private val objectMapper: ObjectMapper,
    private val fallback: AIAnalyzer,
    private val cache: Cache<String, AIAnalysisResult>
) : AIAnalyzer {

    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?
    ): AIAnalysisResult {
        val normalized = InputSanitizer.normalizeUnicode(code)
        val sanitized = runCatching { InputSanitizer.enforceSizeLimit(normalized) }
            .onFailure { e ->
                if (e is InputTooLargeException) {
                    logger.warn { "GigaChat analyze skipped: ${e.message}" }
                    return fallback.analyze(code, language, executionResults, scenarioResults)
                }
            }
            .getOrNull() ?: return fallback.analyze(code, language, executionResults, scenarioResults)

        val cacheKey = cacheKey(sanitized, language)
        cache.getIfPresent(cacheKey)?.let {
            logger.debug { "GigaChat analyze cache hit key=${cacheKey.take(12)}" }
            return it
        }

        val result = runCatching { callAndParse(sanitized, language, executionResults) }
            .onFailure { logger.warn(it) { "GigaChat analyze failed, falling back to rule-based" } }
            .getOrNull()
            ?: fallback.analyze(sanitized, language, executionResults, scenarioResults)

        cache.put(cacheKey, result)
        return result
    }

    override fun explainError(code: String, language: String, error: String, testInput: String): String =
        fallback.explainError(code, language, error, testInput)

    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment =
        fallback.assessCodeQuality(code, language)

    private fun callAndParse(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>
    ): AIAnalysisResult {
        val raw = client.chatCompletion(
            systemPrompt = AnalyzerPrompts.systemPrompt(),
            userPrompt = AnalyzerPrompts.userPrompt(code, language)
        )
        val cleaned = stripJsonFences(raw)
        val payload = objectMapper.readValue(cleaned, GigaChatAnalysisPayload::class.java)
        return mapPayload(payload, executionResults)
    }

    private fun mapPayload(
        payload: GigaChatAnalysisPayload,
        executionResults: List<SandboxExecutionResult>
    ): AIAnalysisResult {
        val rawQuality = payload.codeQuality.coerceIn(0, 100)
        val hasFailure = executionResults.any { it.status != ExecutionStatus.SUCCESS }
        val quality = if (hasFailure) minOf(rawQuality, FAILED_CODE_QUALITY_CAP) else rawQuality
        val issues = payload.issues.take(20).map { msg ->
            CodeIssue(
                type = IssueType.CODE_SMELL,
                severity = Severity.MINOR,
                line = null,
                message = InputSanitizer.stripUnsafeOutput(msg),
                suggestion = ""
            )
        }
        val complexity = runCatching { CodeComplexity.valueOf(payload.complexity.uppercase()) }
            .getOrDefault(CodeComplexity.MEDIUM)
        return AIAnalysisResult(
            codeQuality = quality,
            issues = issues,
            recommendations = payload.recommendations.take(20).map(InputSanitizer::stripUnsafeOutput),
            explanation = InputSanitizer.stripUnsafeOutput(payload.explanation),
            complexity = complexity,
            modelVersion = "gigachat"
        )
    }

    private fun stripJsonFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutOpen = trimmed.removePrefix("```json").removePrefix("```").trimStart()
        val end = withoutOpen.lastIndexOf("```")
        return (if (end >= 0) withoutOpen.substring(0, end) else withoutOpen).trim()
    }

    private fun cacheKey(code: String, language: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(language.lowercase().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(code.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
