package ru.aianalyzer.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.model.SandboxExecutionResult

private val logger = KotlinLogging.logger {}

/**
 * AI Analyzer implementation (stub for ChatGPT)
 */
class ChatGPTAnalyzer(
    private val apiKey: String,
    private val model: String = "gpt-3.5-turbo"
) : AIAnalyzer {

    init {
        logger.info { "ChatGPTAnalyzer initialized with model: $model (API key present: ${apiKey.isNotEmpty()})" }
    }

    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?
    ): AIAnalysisResult {
        // For now, delegate to simple analyzer
        // In production, this would call ChatGPT API
        return SimpleRuleBasedAnalyzer().analyze(code, language, executionResults, scenarioResults)
    }

    override fun explainError(code: String, language: String, error: String, testInput: String): String {
        return SimpleRuleBasedAnalyzer().explainError(code, language, error, testInput)
    }

    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment {
        return SimpleRuleBasedAnalyzer().assessCodeQuality(code, language)
    }
}
