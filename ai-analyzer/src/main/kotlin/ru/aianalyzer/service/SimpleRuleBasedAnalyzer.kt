package ru.aianalyzer.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.model.*

private val logger = KotlinLogging.logger {}

/**
 * Simple rule-based analyzer (fallback when AI is not available)
 */
class SimpleRuleBasedAnalyzer : AIAnalyzer {
    
    override fun analyze(
        code: String,
        language: String,
        executionResults: List<SandboxExecutionResult>,
        scenarioResults: List<ScenarioResult>?
    ): AIAnalysisResult {
        val issues = mutableListOf<CodeIssue>()
        val recommendations = mutableListOf<String>()
        
        val failedResults = executionResults.filter { it.status != ExecutionStatus.SUCCESS }
        
        failedResults.forEach { result ->
            when (result.status) {
                ExecutionStatus.COMPILATION_ERROR -> {
                    issues.add(CodeIssue(
                        type = IssueType.BUG,
                        severity = Severity.CRITICAL,
                        line = null,
                        message = "Compilation error detected",
                        suggestion = "Check syntax and imports"
                    ))
                }
                ExecutionStatus.TIME_LIMIT_EXCEEDED -> {
                    issues.add(CodeIssue(
                        type = IssueType.PERFORMANCE,
                        severity = Severity.MAJOR,
                        line = null,
                        message = "Time limit exceeded",
                        suggestion = "Optimize algorithm complexity"
                    ))
                    recommendations.add("Review algorithm complexity")
                }
                ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> {
                    issues.add(CodeIssue(
                        type = IssueType.PERFORMANCE,
                        severity = Severity.MAJOR,
                        line = null,
                        message = "Memory limit exceeded",
                        suggestion = "Reduce memory usage"
                    ))
                }
                ExecutionStatus.RUNTIME_ERROR -> {
                    val errorMsg = result.error ?: "Unknown error"
                    issues.add(CodeIssue(
                        type = IssueType.BUG,
                        severity = Severity.CRITICAL,
                        line = null,
                        message = "Runtime error: $errorMsg",
                        suggestion = "Check for null pointers, array bounds"
                    ))
                }
                else -> {}
            }
        }
        
        val codeQuality = calculateCodeQuality(code, executionResults, issues)
        
        return AIAnalysisResult(
            codeQuality = codeQuality,
            issues = issues,
            recommendations = recommendations.ifEmpty { listOf("Code looks good!") },
            explanation = buildExplanation(executionResults, failedResults),
            complexity = estimateComplexity(code)
        )
    }
    
    override fun explainError(code: String, language: String, error: String, testInput: String): String {
        return buildString {
            appendLine("Error Analysis:")
            appendLine("Error: $error")
            appendLine()
            appendLine("Common causes:")
            appendLine("- Logic error")
            appendLine("- Invalid input handling")
            appendLine()
            appendLine("How to fix:")
            appendLine("1. Read error message carefully")
            appendLine("2. Check line numbers")
            appendLine("3. Test with simple inputs")
        }
    }
    
    override fun assessCodeQuality(code: String, language: String): CodeQualityAssessment {
        return CodeQualityAssessment(
            overallScore = 70,
            readability = 70,
            maintainability = 70,
            efficiency = 70,
            security = 70,
            strengths = listOf("Code compiles"),
            weaknesses = emptyList()
        )
    }
    
    private fun calculateCodeQuality(code: String, results: List<SandboxExecutionResult>, issues: List<CodeIssue>): Int {
        val baseScore = when {
            results.all { it.status == ExecutionStatus.SUCCESS } -> 90
            results.count { it.status == ExecutionStatus.SUCCESS } > results.size / 2 -> 70
            else -> 50
        }
        
        val issuePenalty = issues.count { it.severity in listOf(Severity.BLOCKER, Severity.CRITICAL) } * 10
        return maxOf(0, baseScore - issuePenalty)
    }
    
    private fun buildExplanation(results: List<SandboxExecutionResult>, failed: List<SandboxExecutionResult>): String {
        return buildString {
            val passed = results.count { it.status == ExecutionStatus.SUCCESS }
            val total = results.size
            
            appendLine("Code was tested with $total executions.")
            appendLine("Passed: $passed/$total")
            appendLine()
            
            if (failed.isEmpty()) {
                appendLine("Great job! All tests passed.")
            } else {
                appendLine("Some tests failed:")
                failed.forEachIndexed { index, result ->
                    appendLine("${index + 1}. ${result.status}")
                    val errorMsg = result.error
                    if (errorMsg != null) {
                        appendLine("   Error: ${errorMsg.take(200)}")
                    }
                }
            }
        }
    }
    
    private fun estimateComplexity(code: String): CodeComplexity {
        val nestedLoops = Regex("""(for|while).*\n.*\n.*\n.*(for|while)""").containsMatchIn(code)
        return when {
            nestedLoops -> CodeComplexity.HIGH
            code.lines().size > 50 -> CodeComplexity.MEDIUM
            else -> CodeComplexity.LOW
        }
    }
}
