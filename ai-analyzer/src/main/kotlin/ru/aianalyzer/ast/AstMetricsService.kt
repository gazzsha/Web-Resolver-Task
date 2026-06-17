package ru.aianalyzer.ast

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Dispatches AST/regex-based metric extraction to the appropriate language-specific analyzer.
 *
 * This service is the single entry point for all structural code analysis in the neuro-symbolic
 * pipeline. It is designed to be defensive: any exception during analysis is caught, logged,
 * and replaced with [AstFact.empty] so the LLM pipeline is never interrupted.
 *
 * Supported languages (case-insensitive): `java`, `python`, `kotlin`.
 * All other languages return [AstFact.empty].
 */
public class AstMetricsService {

    private val javaAnalyzer = JavaAstAnalyzer()
    private val pythonAnalyzer = PythonRegexAnalyzer()
    private val kotlinAnalyzer = KotlinAstAnalyzer()

    /**
     * Extracts structural facts from [code] written in [language].
     *
     * @param code Raw source code submitted by the student.
     * @param language Programming language identifier (e.g. "java", "Python", "KOTLIN").
     * @return Deterministic [AstFact] or [AstFact.empty] if extraction fails.
     */
    public fun extract(code: String, language: String): AstFact {
        // Cap user-controlled language identifier so a 100 KB "language" string can't bloat
        // either the empty-fact label or any downstream prompt that embeds AstFact.language.
        val safeLanguage = language.take(MAX_LANGUAGE_LEN)
        if (code.isBlank()) return AstFact.empty(safeLanguage)

        return when (safeLanguage.lowercase().trim()) {
            "java" -> runSafely(safeLanguage) { javaAnalyzer.analyze(code) }
            "python" -> runSafely(safeLanguage) { pythonAnalyzer.analyze(code) }
            "kotlin" -> runSafely(safeLanguage) { kotlinAnalyzer.analyze(code) }
            else -> {
                log.debug { "AstMetricsService: unsupported language '$safeLanguage', returning empty fact" }
                AstFact.empty(safeLanguage)
            }
        }
    }

    private companion object {
        const val MAX_LANGUAGE_LEN = 32
    }

    private inline fun runSafely(language: String, block: () -> AstFact): AstFact =
        try {
            block()
        } catch (e: Exception) {
            log.warn(e) { "AST analysis failed for language '$language' — falling back to empty fact" }
            AstFact.empty(language)
        }
}
