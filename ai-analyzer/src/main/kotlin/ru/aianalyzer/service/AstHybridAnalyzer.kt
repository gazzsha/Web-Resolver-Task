package ru.aianalyzer.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import ru.aianalyzer.ast.AstFact
import ru.aianalyzer.ast.AstMetricsService
import ru.aianalyzer.ast.spotlightForPrompt
import ru.aianalyzer.metrics.AiAnalyzerMetrics
import ru.aianalyzer.prompt.AnalyzerPrompts
import ru.aianalyzer.sanitize.InputSanitizer
import ru.aianalyzer.sanitize.InputTooLargeException
import ru.sandbox.model.SandboxExecutionResult

/**
 * If AST analysis detected a suspicious constant-return stub AND the LLM gave it a high quality
 * score, clamp the score down. This is a second layer of defence beyond the V4 cross-check in
 * [GigaChatAnalyzer].
 */
private const val AST_SUSPICIOUS_QUALITY_CAP = 60

private val logger = KotlinLogging.logger {}

/**
 * Neuro-symbolic hybrid analyser.
 *
 * Workflow:
 * 1. Extract deterministic [AstFact]s from the submitted code.
 * 2. Inject those facts into the LLM user-prompt via [GigaChatAnalyzer.analyzeWithExtraContext].
 * 3. Apply an additional cross-check: if AST flagged the code as a suspicious constant-return stub
 *    and the LLM reported codeQuality > 60, clamp the quality down to 60.
 * 4. Prepend AST summary to the explanation so the student can see what was detected.
 * 5. Fall back to [SimpleRuleBasedAnalyzer] if the LLM call fails entirely.
 */
class AstHybridAnalyzer(
    private val gigaChatAnalyzer: GigaChatAnalyzer,
    private val astMetricsService: AstMetricsService,
    private val fallback: SimpleRuleBasedAnalyzer,
    private val metrics: AiAnalyzerMetrics? = null
) : AIAnalyzer {

    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?,
        taskContext: AnalyzeContext?
    ): AIAnalysisResult {
        val sample = metrics?.startLatencyTimer()
        var outcome = AiAnalyzerMetrics.OUTCOME_SUCCESS
        try {
            val result = doAnalyze(code, language, executionResults, scenarioResults, taskContext) { newOutcome ->
                outcome = newOutcome
            }
            return result
        } finally {
            if (sample != null) {
                metrics.stopLatencyTimer(sample, AiAnalyzerMetrics.VARIANT_AST_HYBRID, outcome)
            }
            metrics?.recordCall(AiAnalyzerMetrics.VARIANT_AST_HYBRID, outcome)
        }
    }

    private inline fun doAnalyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?,
        taskContext: AnalyzeContext?,
        onOutcome: (String) -> Unit
    ): AIAnalysisResult {
        // 0. Normalise + size-limit BEFORE building the AST prompt so that V2 (Unicode masking)
        //    and V6 (token exhaustion) cannot be bypassed through the ast-hybrid path. AST is
        //    extracted on the normalised text — full-width / zero-width tricks can't hide loops.
        val normalized = InputSanitizer.normalizeUnicode(code)
        val sanitized = runCatching { InputSanitizer.enforceSizeLimit(normalized) }
            .getOrElse { e ->
                if (e is InputTooLargeException) {
                    logger.warn { "AstHybrid skipped (oversize): ${e.message}" }
                    onOutcome(AiAnalyzerMetrics.OUTCOME_FALLBACK)
                    val baseResult = fallback.analyze(code, language, executionResults, scenarioResults, taskContext)
                    return baseResult.withAstAnnotation(AstFact.empty(language), usedFallback = true)
                }
                throw e
            }

        // 1. Extract AST facts on the sanitised code (never throws — returns AstFact.empty on failure)
        val astFact: AstFact = runCatching { astMetricsService.extract(sanitized, language) }
            .onFailure { logger.warn(it) { "AST extraction failed, using empty facts" } }
            .getOrElse { AstFact.empty(language) }

        MDC.put("astLanguage", language)
        logger.debug {
            "AstHybridAnalyzer: lang=$language loop=${astFact.hasLoop} " +
                "recursion=${astFact.hasRecursion} cc=${astFact.cyclomaticComplexity} " +
                "suspiciousConst=${astFact.suspiciousReturnsConstant}"
        }

        // 2. Build enriched user prompt containing the AST facts block + task context (verdict, description).
        //    P0-3: AST + verdict + description идут вместе, поэтому используем userPromptFull
        //    напрямую. spotlightForPrompt укладывает AST-JSON в <AST_FACTS>...</AST_FACTS> блок.
        val astSpotlight = astFact.spotlightForPrompt()
        val userPromptWithAst = AnalyzerPrompts.userPromptFull(
            code = sanitized,
            language = language,
            astFactsBlock = astSpotlight,
            taskContext = taskContext
        )

        // 3. Call LLM with AST-enriched prompt
        val llmResult: AIAnalysisResult = runCatching {
            gigaChatAnalyzer.analyzeWithExtraContext(
                code = sanitized,
                language = language,
                executionResults = executionResults,
                scenarioResults = scenarioResults,
                extraContext = userPromptWithAst
            )
        }.onFailure { e ->
            logger.warn(e) { "GigaChatAnalyzer threw during AST-hybrid analysis, delegating to fallback" }
        }.getOrElse {
            onOutcome(AiAnalyzerMetrics.OUTCOME_FALLBACK)
            val baseResult = fallback.analyze(sanitized, language, executionResults, scenarioResults, taskContext)
            return baseResult.withAstAnnotation(astFact, usedFallback = true)
        }

        // 4. Cross-check: if the LLM used rule-based internally (modelVersion != "gigachat"),
        //    annotate and return as-is (the rule-based already does the sandbox clamp).
        if (llmResult.modelVersion == "rule-based") {
            onOutcome(AiAnalyzerMetrics.OUTCOME_FALLBACK)
            return llmResult.withAstAnnotation(astFact, usedFallback = true)
        }

        // 5. AST cross-check: suspicious constant stub + high quality → clamp
        val clampedQuality = if (astFact.suspiciousReturnsConstant && llmResult.codeQuality > AST_SUSPICIOUS_QUALITY_CAP) {
            logger.info {
                "AstHybridAnalyzer: suspiciousReturnsConstant=true, clamping codeQuality " +
                    "${llmResult.codeQuality} → $AST_SUSPICIOUS_QUALITY_CAP"
            }
            AST_SUSPICIOUS_QUALITY_CAP
        } else {
            llmResult.codeQuality
        }

        return llmResult
            .copy(
                codeQuality = clampedQuality,
                modelVersion = "ast-hybrid"
            )
            .withAstAnnotation(astFact, usedFallback = false)
    }

    override fun explainError(code: String, language: String, error: String, testInput: String): String =
        gigaChatAnalyzer.explainError(code, language, error, testInput)

    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment =
        gigaChatAnalyzer.assessCodeQuality(code, language)

    // ----- helpers -----

    private fun AIAnalysisResult.withAstAnnotation(astFact: AstFact, usedFallback: Boolean): AIAnalysisResult {
        val astSummary = buildString {
            append("AST-факты: ")
            append("язык=${astFact.language}, ")
            append("цикл=${astFact.hasLoop}, ")
            append("рекурсия=${astFact.hasRecursion}, ")
            append("CC=${astFact.cyclomaticComplexity}, ")
            append("строк=${astFact.lineCount}")
            if (astFact.suspiciousReturnsConstant) append(", [подозрение: возврат константы]")
            if (usedFallback) append(" | использован fallback-анализатор")
        }
        val updatedExplanation = "$astSummary\n\n$explanation"
        return copy(
            explanation = updatedExplanation,
            modelVersion = if (modelVersion == "rule-based" || usedFallback) "ast-hybrid-fallback" else "ast-hybrid"
        )
    }
}
