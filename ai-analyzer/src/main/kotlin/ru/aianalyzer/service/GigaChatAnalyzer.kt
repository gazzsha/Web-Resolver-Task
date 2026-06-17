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
 * Вычисляет динамическую верхнюю границу codeQuality, пропорциональную
 * отношению passed/total (защита от V4 — LLM-галлюцинации «code is great»
 * при упавших тестах).
 *
 * Таблица соответствия:
 * - total == 0             → [Int.MAX_VALUE] (нет ограничения)
 * - passed == total        → [Int.MAX_VALUE] (все прошли, нет ограничения)
 * - passed == 0            → 20
 * - passed == total - 1    → 70  (приоритет перед «passed == 1» для total == 2)
 * - passed == 1            → 30
 * - passed * 2 <= total    → 50  (половина или меньше)
 * - иначе                  → 70  (больше половины, но не все)
 *
 * Cap является **верхней** границей — если LLM вернул значение ниже cap,
 * оно сохраняется без подъёма.
 */
internal fun dynamicQualityCap(passed: Int, total: Int): Int {
    if (total == 0) return Int.MAX_VALUE
    if (passed == total) return Int.MAX_VALUE
    if (passed == 0) return 20
    if (passed == total - 1) return 70
    if (passed == 1) return 30
    if (passed * 2 <= total) return 50
    return 70
}

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
    private val disableVerdictGuards: Boolean = false,
    // ── Feature flag (P0-X, Step 6a/B.6) ───────────────────────────────────────
    // Когда true — методы [explainError] и [assessCodeQuality] делают
    // отдельный (короткий) вызов LLM с компактным промптом вместо немедленной
    // делегации в rule-based fallback. При любой ошибке/таймауте/невалидном
    // ответе вызовы всё равно возвращают результат fallback, поэтому фича
    // безопасна для прода. analyze()-pipeline (использованный в Level-2
    // эксперименте) этим флагом НЕ затрагивается.
    private val explainViaLlm: Boolean = false
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

    override fun explainError(code: String, language: String, error: String, testInput: String): String {
        if (!explainViaLlm) return fallback.explainError(code, language, error, testInput)
        return explainErrorWithLlm(code, language, error, testInput)
            ?: fallback.explainError(code, language, error, testInput)
    }

    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment {
        if (!explainViaLlm) return fallback.assessCodeQuality(code, language)
        return assessCodeQualityWithLlm(code, language)
            ?: fallback.assessCodeQuality(code, language)
    }

    /**
     * Короткий LLM-вызов с компактным промптом «объясни одну ошибку».
     * Не использует analyze()-pipeline и не затрагивает Level-2-эксперимент.
     *
     * Защитные слои сохранены: normalizeUnicode, enforceSizeLimit (на code),
     * sentinel-маркеры в промпте, stripUnsafeOutput + enforceImpersonalTone
     * на ответе LLM. JSON-схему не используем — здесь нужен plain-text абзац.
     *
     * @return объяснение от LLM или null при ошибке/таймауте/слишком большом
     *   коде — вызывающая сторона должна сделать fallback.
     */
    private fun explainErrorWithLlm(code: String, language: String, error: String, testInput: String): String? {
        val normalized = InputSanitizer.normalizeUnicode(code)
        val sanitized = runCatching { InputSanitizer.enforceSizeLimit(normalized) }.getOrElse { return null }
        val sanitizedError = InputSanitizer.normalizeUnicode(error).take(2000)
        val sanitizedInput = InputSanitizer.normalizeUnicode(testInput).take(1000)
        return runCatching {
            val raw = client.chatCompletion(
                systemPrompt = AnalyzerPrompts.explainErrorSystemPrompt(),
                userPrompt = AnalyzerPrompts.explainErrorUserPrompt(sanitized, language, sanitizedError, sanitizedInput)
            )
            val cleaned = stripJsonFences(raw).trim()
            if (cleaned.isBlank()) return@runCatching null
            InputSanitizer.enforceImpersonalTone(InputSanitizer.stripUnsafeOutput(cleaned)).take(4000)
        }.onFailure { logger.warn(it) { "explainErrorWithLlm failed, will fallback to rule-based" } }
            .getOrNull()
    }

    /**
     * Короткий LLM-вызов «оцени качество кода по 5 осям». Возвращает
     * [CodeQualityAssessment] либо null при сбое — вызывающая сторона должна
     * сделать fallback. Защитные слои аналогичны [explainErrorWithLlm].
     */
    private fun assessCodeQualityWithLlm(code: String, language: String): CodeQualityAssessment? {
        val normalized = InputSanitizer.normalizeUnicode(code)
        val sanitized = runCatching { InputSanitizer.enforceSizeLimit(normalized) }.getOrElse { return null }
        return runCatching {
            val raw = client.chatCompletion(
                systemPrompt = AnalyzerPrompts.assessQualitySystemPrompt(),
                userPrompt = AnalyzerPrompts.assessQualityUserPrompt(sanitized, language)
            )
            val cleaned = stripJsonFences(raw)
            val node = objectMapper.readTree(cleaned)
            CodeQualityAssessment(
                overallScore = node.get("overallScore")?.asInt(70)?.coerceIn(0, 100) ?: 70,
                readability = node.get("readability")?.asInt(70)?.coerceIn(0, 100) ?: 70,
                maintainability = node.get("maintainability")?.asInt(70)?.coerceIn(0, 100) ?: 70,
                efficiency = node.get("efficiency")?.asInt(70)?.coerceIn(0, 100) ?: 70,
                security = node.get("security")?.asInt(70)?.coerceIn(0, 100) ?: 70,
                strengths = node.get("strengths")?.mapNotNull { it?.asText() }?.take(8)
                    ?.map { InputSanitizer.enforceImpersonalTone(InputSanitizer.stripUnsafeOutput(it)) }
                    ?: emptyList(),
                weaknesses = node.get("weaknesses")?.mapNotNull { it?.asText() }?.take(8)
                    ?.map { InputSanitizer.enforceImpersonalTone(InputSanitizer.stripUnsafeOutput(it)) }
                    ?: emptyList()
            )
        }.onFailure { logger.warn(it) { "assessCodeQualityWithLlm failed, will fallback to rule-based" } }
            .getOrNull()
    }

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

    /**
     * @param astSuspiciousReturnsConstant Сигнал AST-детектора: true означает, что код
     *   выглядит как стаб, возвращающий константу (e.g. `return 42`). Если этот флаг
     *   установлен И passed == 0 И total > 0, применяется экстремальный cap = 10.
     *   По умолчанию false — когда mapPayload вызывается напрямую без AST-контекста.
     *   TODO(AstHybridAnalyzer): пробросить реальное astFact.suspiciousReturnsConstant
     *   через pipeline, когда AstHybridAnalyzer будет рефакторен для вызова mapPayload
     *   вместо своего собственного AST_SUSPICIOUS_QUALITY_CAP-каскада.
     */
    internal fun mapPayload(
        payload: GigaChatAnalysisPayload,
        executionResults: List<SandboxExecutionResult>,
        astSuspiciousReturnsConstant: Boolean = false
    ): AIAnalysisResult {
        val rawQuality = payload.codeQuality.coerceIn(0, 100)
        val total = executionResults.size
        val passed = executionResults.count { it.status == ExecutionStatus.SUCCESS }
        // Динамический cap: пропорционален passed/total.
        // EXPERIMENT-ONLY: ветка B3 проходит весь раннер с [disableVerdictGuards]=true,
        // чтобы в SUMMARY можно было увидеть injection_success_rate без guard'ов и
        // сделать ablation. Все остальные варианты идут по штатному cascading-пути.
        val cap = when {
            disableVerdictGuards -> Int.MAX_VALUE
            astSuspiciousReturnsConstant && passed == 0 && total > 0 -> {
                logger.warn { "GigaChat: suspiciousReturnsConstant=true + allFailed → extreme cap=10 (quality=$rawQuality)" }
                10
            }
            else -> dynamicQualityCap(passed, total)
        }
        if (cap != Int.MAX_VALUE && rawQuality > cap) {
            logger.warn { "GigaChat output contradicts sandbox: passed=$passed/$total quality=$rawQuality → clamp $cap" }
        }
        val quality = minOf(rawQuality, cap)
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
            recommendations = payload.recommendations.take(20)
                .map { InputSanitizer.enforceImpersonalTone(InputSanitizer.stripUnsafeOutput(it)) },
            explanation = InputSanitizer.enforceImpersonalTone(InputSanitizer.stripUnsafeOutput(payload.explanation)),
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
