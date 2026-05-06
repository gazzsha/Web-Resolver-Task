package ru.taskresolver.service

import model.AIAnalysisResponse
import model.AIAnalysisSummary
import model.CodeComplexity
import model.CodeIssue
import model.IssueType
import model.ScenarioResultDetail
import model.Severity
import model.StepResultDetail
import model.TaskResultResponse
import model.TaskStatus
import model.TestResultDetail
import model.TestStatus
import model.Verdict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.db.entity.TaskResultEntity
import ru.db.repository.TaskResultRepository
import java.util.UUID

@Service
class TaskResultService(
    private val taskResultRepository: TaskResultRepository
) {

    fun getTaskResult(taskId: UUID): TaskResultResponse? {
        val entity = taskResultRepository.findBySubmissionId(taskId) ?: return null
        return mapToResponse(entity)
    }

    fun getTestResults(testId: UUID): List<TaskResultResponse> {
        return taskResultRepository.findByTestId(testId)
            .map { mapToResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getAIAnalysis(taskId: UUID): AIAnalysisResponse? {
        val entity = taskResultRepository.findBySubmissionId(taskId) ?: return null
        val ai = entity.aiAnalysis ?: return null
        if (ai.modelVersion == "rule-based") return null
        return AIAnalysisResponse()
            .codeQuality(ai.codeQualityScore)
            .issues(ai.issues.map { issue ->
                CodeIssue()
                    .type(IssueType.fromValue(issue.type.name))
                    .severity(Severity.fromValue(issue.severity.name))
                    .line(issue.line)
                    .message(issue.message)
                    .suggestion(issue.suggestion)
            })
            .recommendations(ai.recommendations)
            .explanation(ai.explanation)
            .complexity(CodeComplexity.fromValue(ai.complexity.name))
    }

    @Transactional(readOnly = true)
    fun getTaskEntityBySubmissionId(submissionId: UUID): TaskResultEntity? {
        return taskResultRepository.findBySubmissionId(submissionId)
    }

    private fun mapToResponse(entity: TaskResultEntity): TaskResultResponse {
        return TaskResultResponse()
            .taskId(entity.taskId)
            .testId(entity.testId)
            .status(TaskStatus.fromValue(entity.status.name))
            .totalTests(entity.totalTests)
            .passedTests(entity.passedTests)
            .totalExecutionTimeMs(entity.totalExecutionTimeMs.toInt())
            .memoryUsedKb(entity.memoryUsedKb.toInt())
            .testResults(entity.testResults.map { testResult ->
                TestResultDetail()
                    .testId(testResult.testId)
                    .status(TestStatus.fromValue(testResult.status.name))
                    .verdict(Verdict.fromValue(testResult.verdict.name))
                    .output(testResult.output)
                    .error(testResult.error)
                    .executionTimeMs(testResult.executionTimeMs.toInt())
                    .memoryUsedKb(testResult.memoryUsedKb.toInt())
            })
            .scenarioResults(entity.scenarioResults.map { scenarioResult ->
                ScenarioResultDetail()
                    .scenarioId(scenarioResult.scenarioId)
                    .status(TestStatus.fromValue(scenarioResult.status.name))
                    .stepResults(scenarioResult.stepResults.map { stepResult ->
                        StepResultDetail()
                            .stepNumber(stepResult.stepNumber)
                            .status(TestStatus.fromValue(stepResult.status.name))
                            .actualOutput(stepResult.actualOutput)
                            .expectedOutput(stepResult.expectedOutput)
                            .stateMatches(stepResult.stateMatches)
                    })
                    .finalState(scenarioResult.finalState)
            })
            .aiAnalysis(entity.aiAnalysis?.takeIf { it.modelVersion != "rule-based" }?.let { ai ->
                AIAnalysisSummary()
                    .codeQuality(ai.codeQualityScore)
                    .issueCount(ai.issues.size)
                    .complexity(CodeComplexity.fromValue(ai.complexity.name))
                    .explanation(ai.explanation)
            })
            .createdAt(java.time.OffsetDateTime.ofInstant(entity.createdAt, java.time.ZoneOffset.UTC))
            .code(entity.code)
            .language(model.Lang.fromValue(entity.language))
    }
}
