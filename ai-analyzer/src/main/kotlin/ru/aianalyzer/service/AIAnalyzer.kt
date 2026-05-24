package ru.aianalyzer.service

import ru.sandbox.model.SandboxExecutionResult

/**
 * AI Analyzer for code analysis and educational feedback
 * 
 * This is the core innovation of the system:
 * - Analyzes code structure and quality
 * - Explains errors in natural language
 * - Provides personalized recommendations
 * - Detects code smells and security issues
 * 
 * Hybrid verification approach:
 * 1. Test Engine → factual errors
 * 2. AI → explanation and recommendations
 * 3. (Optional) AST → structural confirmation
 */
interface AIAnalyzer {

    /**
     * Analyze code and execution results.
     *
     * [taskContext] (P0-3) — необязательный контекст задачи: условие, агрегаты
     * sandbox-вердикта, первая ошибка. Подмешивается в user-message, чтобы LLM
     * не выдумывал содержание задачи и пройдены ли тесты.
     */
    fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>? = null,
        taskContext: AnalyzeContext? = null
    ): AIAnalysisResult
    
    /**
     * Get explanation for specific error
     */
    fun explainError(
        code: String,
        language: String,
        error: String,
        testInput: String
    ): String
    
    /**
     * Get code quality score
     */
    fun assessCodeQuality(
        code: String,
        language: String
    ): CodeQualityAssessment
}

/**
 * Контекст задачи для содержательного промпта (P0-3).
 *
 * Передаётся опционально. Если null — анализатор работает по старому пути
 * (только код + язык). Если заполнен — модель получает условие задачи и
 * агрегаты sandbox-вердикта, что снижает галлюцинации про содержание кода
 * и факт прохождения тестов.
 *
 * @property taskDescription Условие задачи на естественном языке (как в БД).
 * @property passedTests Сколько тестов прошло; null если данные недоступны.
 * @property totalTests Сколько всего тестов; null если данные недоступны.
 * @property overallVerdict Итоговый verdict ("OK", "WRONG_ANSWER", и т.п.); null если неизвестен.
 * @property firstError stderr/output первого упавшего теста, обрезано до 500 символов.
 */
data class AnalyzeContext(
    val taskDescription: String? = null,
    val passedTests: Int? = null,
    val totalTests: Int? = null,
    val overallVerdict: String? = null,
    val firstError: String? = null
)

/**
 * Scenario result for AI analysis
 */
data class ScenarioResult(
    val scenarioId: java.util.UUID,
    val status: String,
    val stepResults: List<StepResult>,
    val finalState: String?
)

data class StepResult(
    val stepNumber: Int,
    val status: String,
    val actualOutput: String?,
    val expectedOutput: String?,
    val stateMatches: Boolean
)

/**
 * AI analysis result
 */
data class AIAnalysisResult(
    val codeQuality: Int, // 0-100
    val issues: List<CodeIssue>,
    val recommendations: List<String>,
    val explanation: String,
    val complexity: CodeComplexity,
    val modelVersion: String = "unknown"
)

/**
 * Code issue found by AI
 */
data class CodeIssue(
    val type: IssueType,
    val severity: Severity,
    val line: Int?,
    val message: String,
    val suggestion: String
)

enum class IssueType {
    BUG,
    CODE_SMELL,
    SECURITY,
    PERFORMANCE,
    STYLE
}

enum class Severity {
    BLOCKER,
    CRITICAL,
    MAJOR,
    MINOR,
    INFO
}

enum class CodeComplexity {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

/**
 * Code quality assessment
 */
data class CodeQualityAssessment(
    val overallScore: Int, // 0-100
    val readability: Int,
    val maintainability: Int,
    val efficiency: Int,
    val security: Int,
    val strengths: List<String>,
    val weaknesses: List<String>
)
