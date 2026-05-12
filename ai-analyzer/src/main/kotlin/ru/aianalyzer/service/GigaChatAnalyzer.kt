package ru.aianalyzer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.aianalyzer.client.GigaChatAnalysisPayload
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.prompt.AnalyzerPrompts
import ru.aianalyzer.prompt.PromptVariant
import ru.aianalyzer.sanitize.InputSanitizer
import ru.aianalyzer.sanitize.InputTooLargeException
import ru.aianalyzer.validation.SchemaValidationException
import ru.aianalyzer.validation.SchemaValidator
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
    private val cache: Cache<String, AIAnalysisResult>,
    private val schemaValidator: SchemaValidator,
    private val promptVariant: PromptVariant = PromptVariant.ZERO_SHOT
) : AIAnalyzer {

    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?
    ): AIAnalysisResult = analyzeWithExtraContext(code, language, executionResults, scenarioResults, extraContext = null)

    /**
     * Variant of [analyze] that injects additional context (e.g. AST facts) into the user prompt.
     * Used by [ru.aianalyzer.service.AstHybridAnalyzer] to feed deterministic AST data alongside code.
     *
     * When [extraContext] is non-null it is used as the user prompt verbatim instead of the standard
     * [ru.aianalyzer.prompt.AnalyzerPrompts.userPrompt].
     */
    fun analyzeWithExtraContext(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>? = null,
        extraContext: String?
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

        val result = analyzeWithRetry(sanitized, language, executionResults, extraContext)
            ?: fallback.analyze(sanitized, language, executionResults, scenarioResults)

        cache.put(cacheKey, result)
        return result
    }

    private fun analyzeWithRetry(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        extraContext: String? = null
    ): AIAnalysisResult? {
        return try {
            callAndParse(code, language, executionResults, retryHint = null, extraContext = extraContext)
        } catch (schemaErr: SchemaValidationException) {
            logger.warn { "GigaChat schema invalid, retrying once: ${schemaErr.message}" }
            runCatching { callAndParse(code, language, executionResults, retryHint = schemaErr.message ?: "schema mismatch", extraContext = extraContext) }
                .onFailure { logger.warn(it) { "GigaChat retry failed, falling back to rule-based" } }
                .getOrNull()
        } catch (e: Exception) {
            logger.warn(e) { "GigaChat analyze failed, falling back to rule-based" }
            null
        }
    }

    override fun explainError(code: String, language: String, error: String, testInput: String): String =
        fallback.explainError(code, language, error, testInput)

    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment =
        fallback.assessCodeQuality(code, language)

    private fun callAndParse(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        retryHint: String?,
        extraContext: String? = null
    ): AIAnalysisResult {
        val userPrompt = when {
            retryHint != null -> AnalyzerPrompts.userPromptRetry(code, language, retryHint)
            extraContext != null -> extraContext
            else -> AnalyzerPrompts.userPrompt(code, language)
        }
        val raw = client.chatCompletion(
            systemPrompt = AnalyzerPrompts.systemPrompt(promptVariant),
            userPrompt = userPrompt
        )
        val cleaned = stripJsonFences(raw)
        schemaValidator.parseAndValidate(cleaned)
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
