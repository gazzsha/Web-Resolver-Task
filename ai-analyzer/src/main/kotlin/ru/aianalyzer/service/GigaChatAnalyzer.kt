package ru.aianalyzer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.aianalyzer.ast.AstMetricsService
import ru.aianalyzer.ast.spotlightForPrompt
import ru.aianalyzer.client.GigaChatAnalysisPayload
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.metrics.AiAnalyzerMetrics
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
 * Каскад верхних границ codeQuality при провальных результатах sandbox-вердикта
 * (защита от V4 — LLM-галлюцинации «code is great» при упавших тестах).
 *
 * - [FAILED_CODE_QUALITY_CAP] — хотя бы один тест упал → не выше 60.
 * - [ALL_FAILED_CODE_QUALITY_CAP] (P0-4) — НИ ОДИН тест не прошёл → не выше 40.
 *   Это сценарий из P0-3 (заведомо неправильное `print("true")`), где LLM
 *   раньше возвращал codeQuality 70+ с generic-похвалой.
 */
private const val FAILED_CODE_QUALITY_CAP = 60
private const val ALL_FAILED_CODE_QUALITY_CAP = 40

private val logger = KotlinLogging.logger {}

class GigaChatAnalyzer(
    private val client: GigaChatClient,
    private val objectMapper: ObjectMapper,
    private val fallback: AIAnalyzer,
    private val cache: Cache<String, AIAnalysisResult>,
    private val schemaValidator: SchemaValidator,
    private val promptVariant: PromptVariant = PromptVariant.ZERO_SHOT,
    // P0-3: внутренний AST-extractor для случая, когда analyze() вызывают напрямую
    // (без AstHybridAnalyzer-обёртки). Если null — AST блок в user-message не добавляется.
    private val astMetricsService: AstMetricsService? = null,
    // Метрики Prometheus: null когда работает experiment runner без Spring context.
    private val metrics: AiAnalyzerMetrics? = null,
    // ── EXPERIMENT-ONLY ────────────────────────────────────────────────────────
    // Когда true — отключает каскад verdict-cap'ов в [mapPayload]. Используется
    // ИСКЛЮЧИТЕЛЬНО в experiment Runner для варианта B3 (no-guards), чтобы
    // изолировать вклад verdict-guard слоя в защиту от prompt-injection
    // (см. PRE_REGISTRATION.md, H4). В production-конфигурации
    // [ru.aianalyzer.config.AiAnalyzerConfig] этот флаг НЕ выставляется
    // (значение по умолчанию `false`), а unit-тесты в
    // GigaChatAnalyzerTest проверяют, что cap'ы работают.
    private val disableVerdictGuards: Boolean = false
) : AIAnalyzer {

    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?,
        taskContext: AnalyzeContext?
    ): AIAnalysisResult = analyzeWithExtraContext(code, language, executionResults, scenarioResults, extraContext = null, taskContext = taskContext)

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
        extraContext: String?,
        taskContext: AnalyzeContext? = null
    ): AIAnalysisResult {
        val normalized = InputSanitizer.normalizeUnicode(code)
        val sanitized = runCatching { InputSanitizer.enforceSizeLimit(normalized) }
            .onFailure { e ->
                if (e is InputTooLargeException) {
                    logger.warn { "GigaChat analyze skipped: ${e.message}" }
                    metrics?.recordCall(AiAnalyzerMetrics.VARIANT_GIGACHAT, AiAnalyzerMetrics.OUTCOME_FALLBACK)
                    return fallback.analyze(code, language, executionResults, scenarioResults, taskContext)
                }
            }
            .getOrNull() ?: run {
                metrics?.recordCall(AiAnalyzerMetrics.VARIANT_GIGACHAT, AiAnalyzerMetrics.OUTCOME_FALLBACK)
                return fallback.analyze(code, language, executionResults, scenarioResults, taskContext)
            }

        val cacheKey = cacheKey(sanitized, language, executionResults, extraContext, taskContext)
        cache.getIfPresent(cacheKey)?.let {
            logger.debug { "GigaChat analyze cache hit key=${cacheKey.take(12)}" }
            metrics?.recordCall(AiAnalyzerMetrics.VARIANT_GIGACHAT, AiAnalyzerMetrics.OUTCOME_CACHE_HIT)
            return it
        }

        val sample = metrics?.startLatencyTimer()
        val llmResult = analyzeWithRetry(sanitized, language, executionResults, extraContext, taskContext)
        val (finalResult, outcome) = if (llmResult != null) {
            llmResult to AiAnalyzerMetrics.OUTCOME_SUCCESS
        } else {
            fallback.analyze(sanitized, language, executionResults, scenarioResults, taskContext) to
                AiAnalyzerMetrics.OUTCOME_FALLBACK
        }
        if (sample != null) {
            metrics.stopLatencyTimer(sample, AiAnalyzerMetrics.VARIANT_GIGACHAT, outcome)
        }
        metrics?.recordCall(AiAnalyzerMetrics.VARIANT_GIGACHAT, outcome)

        cache.put(cacheKey, finalResult)
        return finalResult
    }

    private fun analyzeWithRetry(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        extraContext: String? = null,
        taskContext: AnalyzeContext? = null
    ): AIAnalysisResult? {
        return try {
            val result = callAndParse(code, language, executionResults, retryHint = null, extraContext = extraContext, taskContext = taskContext)
            metrics?.recordSchemaValidation(valid = true)
            result
        } catch (schemaErr: SchemaValidationException) {
            logger.warn { "GigaChat schema invalid, retrying once: ${schemaErr.message}" }
            metrics?.recordSchemaValidation(valid = false)
            runCatching { callAndParse(code, language, executionResults, retryHint = schemaErr.message ?: "schema mismatch", extraContext = extraContext, taskContext = taskContext) }
                .onSuccess { metrics?.recordSchemaValidation(valid = true) }
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
        extraContext: String? = null,
        taskContext: AnalyzeContext? = null
    ): AIAnalysisResult {
        // Если taskContext или AST-сервис не null — строим полный prompt с условием
        // задачи, sandbox-вердиктом, AST-фактами и plain-text кодом в sentinel-маркерах.
        // Старый путь (без контекста) сохранён для совместимости с unit-тестами,
        // которые мокают callAndParse через analyze("code", "java", ...).
        val effectiveTaskContext = taskContext ?: contextFromExecutionResults(executionResults)
        val astBlock = astMetricsService?.runCatching { extract(code, language).spotlightForPrompt() }
            ?.onFailure { logger.warn(it) { "AST extract failed, omitting block" } }
            ?.getOrNull()
        val userPrompt = when {
            // F-9: retry now carries AST + verdict context so the retry isn't a degraded
            // attempt vs the original. See AnalyzerPrompts.userPromptRetry.
            retryHint != null -> AnalyzerPrompts.userPromptRetry(
                code = code,
                language = language,
                validationError = retryHint,
                astFactsBlock = astBlock,
                taskContext = effectiveTaskContext
            )
            extraContext != null -> extraContext
            astBlock != null || hasMeaningfulContext(effectiveTaskContext) ->
                AnalyzerPrompts.userPromptFull(
                    code = code,
                    language = language,
                    astFactsBlock = astBlock,
                    taskContext = effectiveTaskContext
                )
            else -> AnalyzerPrompts.userPrompt(code, language)
        }
        val raw = client.chatCompletion(
            systemPrompt = AnalyzerPrompts.systemPrompt(promptVariant),
            userPrompt = userPrompt
        )
        val cleaned = stripJsonFences(raw)
        // Dev-only debug: dump first response we see to /tmp for empirical inspection.
        System.getenv("AI_DEBUG_DUMP")?.let { dumpPath ->
            val f = java.io.File(dumpPath)
            if (!f.exists()) f.writeText("=== RAW ===\n$raw\n\n=== CLEANED ===\n$cleaned\n")
        }
        schemaValidator.parseAndValidate(cleaned)
        val payload = objectMapper.readValue(cleaned, GigaChatAnalysisPayload::class.java)
        return mapPayload(payload, executionResults)
    }

    private fun mapPayload(
        payload: GigaChatAnalysisPayload,
        executionResults: List<SandboxExecutionResult>
    ): AIAnalysisResult {
        val rawQuality = payload.codeQuality.coerceIn(0, 100)
        val total = executionResults.size
        val passed = executionResults.count { it.status == ExecutionStatus.SUCCESS }
        val hasFailure = total > 0 && passed < total
        val allFailed = total > 0 && passed == 0
        // P0-4: каскад капов. Полностью провалено → 40, частично → 60, всё ОК → без капа.
        // EXPERIMENT-ONLY: ветка B3 проходит весь раннер с [disableVerdictGuards]=true,
        // чтобы в SUMMARY можно было увидеть injection_success_rate без guard'ов и
        // сделать ablation. Все остальные варианты идут по штатному cascading-пути.
        val quality = when {
            disableVerdictGuards -> rawQuality
            allFailed && rawQuality > ALL_FAILED_CODE_QUALITY_CAP -> {
                logger.warn { "GigaChat output contradicts sandbox: passed=0/$total quality=$rawQuality → clamp $ALL_FAILED_CODE_QUALITY_CAP" }
                ALL_FAILED_CODE_QUALITY_CAP
            }
            hasFailure -> minOf(rawQuality, FAILED_CODE_QUALITY_CAP)
            else -> rawQuality
        }
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
            // B3 (no-guards) маркируется отдельно, чтобы analyze.py легко
            // отделял его прогоны и не путал с штатным production-path.
            modelVersion = if (disableVerdictGuards) "gigachat-no-guards" else "gigachat"
        )
    }

    /**
     * Собирает агрегаты вердикта (passedTests, totalTests, overallVerdict, firstError)
     * из списка SandboxExecutionResult, чтобы передать в prompt даже когда
     * вызывающая сторона не предоставила [AnalyzeContext].
     *
     * Это гарантирует, что блок «Результат проверки sandbox» всегда попадёт в
     * user-message, даже если worker по какой-то причине не пробросил контекст.
     */
    private fun contextFromExecutionResults(results: List<SandboxExecutionResult>): AnalyzeContext {
        if (results.isEmpty()) return AnalyzeContext()
        val total = results.size
        val passed = results.count { it.status == ExecutionStatus.SUCCESS }
        val firstFailure = results.firstOrNull { it.status != ExecutionStatus.SUCCESS }
        val overall = when {
            firstFailure == null -> "OK"
            firstFailure.status == ExecutionStatus.TIME_LIMIT_EXCEEDED -> "TIME_LIMIT_EXCEEDED"
            firstFailure.status == ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> "MEMORY_LIMIT_EXCEEDED"
            firstFailure.status == ExecutionStatus.COMPILATION_ERROR -> "COMPILATION_ERROR"
            firstFailure.status == ExecutionStatus.RUNTIME_ERROR -> "RUNTIME_ERROR"
            else -> "WRONG_ANSWER"
        }
        val firstErr = firstFailure?.let { it.error ?: it.output }?.take(500)
        return AnalyzeContext(
            taskDescription = null,
            passedTests = passed,
            totalTests = total,
            overallVerdict = overall,
            firstError = firstErr
        )
    }

    private fun hasMeaningfulContext(c: AnalyzeContext?): Boolean =
        c != null && (c.taskDescription != null || c.totalTests != null || c.overallVerdict != null)

    private fun stripJsonFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutOpen = trimmed.removePrefix("```json").removePrefix("```").trimStart()
        val end = withoutOpen.lastIndexOf("```")
        return (if (end >= 0) withoutOpen.substring(0, end) else withoutOpen).trim()
    }

    /**
     * Cache key includes everything that can change the analysis outcome:
     *   - language + sanitized code (the obvious inputs);
     *   - sandbox verdict fingerprint, because V4 clamp depends on it — without
     *     this two submissions with same code but different sandbox results
     *     would share a cached AIAnalysisResult and the clamp would be bypassed;
     *   - promptVariant, so a B1f run doesn't pollute the B1 cache and vice versa;
     *   - presence/absence of AST extraContext, so the ast-hybrid path is keyed
     *     separately from a plain gigachat call on the same code.
     */
    private fun cacheKey(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        extraContext: String?,
        taskContext: AnalyzeContext?
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(language.lowercase().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(code.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(promptVariant.name.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update((if (extraContext != null) "withAst" else "plain").toByteArray(Charsets.UTF_8))
        digest.update(0)
        val verdictFp = executionResults.joinToString(",") { it.status.name }
        digest.update(verdictFp.toByteArray(Charsets.UTF_8))
        digest.update(0)
        // P0-3: taskDescription входит в ключ — без этого изменение условия задачи
        // (новая редакция текста) тихо вернёт закэшированный анализ старого условия.
        val descFp = taskContext?.taskDescription?.take(2048) ?: "no-desc"
        digest.update(descFp.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
