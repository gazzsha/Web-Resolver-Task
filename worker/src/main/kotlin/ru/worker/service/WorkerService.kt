package ru.worker.service

import ru.sandbox.model.SandboxExecutionResult
import ru.sandbox.service.DockerSandboxService
import ru.worker.model.*
import ru.aianalyzer.service.AIAnalyzer
import ru.scenarioplayer.ScenarioRunner
import java.util.UUID

/**
 * Worker service for processing tasks from queue with Docker Sandbox
 */
class WorkerService(
    private val testEngine: TestEngine,
    private val scenarioRunner: ScenarioRunner,
    private val aiAnalyzer: AIAnalyzer,
    private val sandboxService: DockerSandboxService? = null
) {

    fun processTask(message: WorkerTaskMessage): WorkerTaskResult {
        val startTime = System.currentTimeMillis()
        val testResults = mutableListOf<TestResult>()
        val scenarioResults = mutableListOf<ru.scenarioplayer.ScenarioResult>()

        try {
            // 1. Run test cases using TestEngine with Docker Sandbox.
            // Early-stop on the first failure (LeetCode-style) so the user
            // gets feedback within the time of one failed test, not N×timeout.
            // Remaining tests are recorded as SKIPPED so the UI keeps
            // showing «passed/total» of the full task, not just the slice
            // that actually ran.
            var stopped = false
            for ((index, testCase) in message.testCases.withIndex()) {
                if (stopped) {
                    testResults.add(
                        TestResult(
                            testId = testCase.testId,
                            status = TestStatus.SKIPPED,
                            verdict = Verdict.WRONG_ANSWER,
                            output = null,
                            error = "Пропущено — первый невалидный тест уже прерывает проверку",
                            executionTimeMs = 0,
                            memoryUsedKb = 0
                        )
                    )
                    continue
                }
                val result = testEngine.runTest(message.code, message.language, testCase)
                testResults.add(result)
                if (result.status != TestStatus.PASSED && index < message.testCases.size - 1) {
                    stopped = true
                }
            }

            // 2. Run scenario tests if present
            message.scenarioTests?.forEach { scenario ->
                val convertedScenario = ru.scenarioplayer.ScenarioTest(
                    scenarioId = scenario.scenarioId,
                    steps = scenario.steps.map { step ->
                        ru.scenarioplayer.ScenarioStep(
                            stepNumber = step.stepNumber,
                            input = step.input,
                            expectedOutput = step.expectedOutput
                        )
                    }
                )
                val scenarioResult = scenarioRunner.runScenario(message.code, message.language, convertedScenario)
                scenarioResults.add(scenarioResult)
            }

            // 3. Determine overall status
            val taskStatus = determineTaskStatus(testResults, scenarioResults)

            // 4. Run AI analysis
            val aiAnalysis = aiAnalyzer.analyze(
                code = message.code,
                language = message.language,
                executionResults = testResults.map { it.toSandboxResult() }
            )

            val endTime = System.currentTimeMillis()

            return WorkerTaskResult(
                taskId = message.taskId,
                testId = message.testId,
                code = message.code,
                language = message.language,
                status = taskStatus,
                testResults = testResults,
                scenarioResults = scenarioResults.map { it.toWorkerScenarioResult() },
                aiAnalysis = aiAnalysis,
                totalExecutionTimeMs = endTime - startTime,
                memoryUsedKb = testResults.sumOf { it.memoryUsedKb }
            )
        } catch (e: Exception) {
            return WorkerTaskResult(
                taskId = message.taskId,
                testId = message.testId,
                code = message.code,
                language = message.language,
                status = TaskStatus.ERROR,
                testResults = testResults,
                scenarioResults = emptyList(),
                aiAnalysis = null,
                totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                memoryUsedKb = 0
            )
        }
    }

    private fun determineTaskStatus(
        testResults: List<TestResult>,
        scenarioResults: List<ru.scenarioplayer.ScenarioResult>
    ): TaskStatus {
        val allTestPassed = testResults.all { it.status == TestStatus.PASSED }
        val allScenarioPassed = scenarioResults.all { it.status == "PASSED" }

        return when {
            testResults.isEmpty() && scenarioResults.isEmpty() -> TaskStatus.SUCCESS
            allTestPassed && allScenarioPassed -> TaskStatus.SUCCESS
            testResults.any { it.status == TestStatus.ERROR } -> TaskStatus.FAILED
            testResults.any { it.status == TestStatus.PASSED } -> TaskStatus.PARTIAL_SUCCESS
            else -> TaskStatus.FAILED
        }
    }
}

private fun TestResult.toSandboxResult(): SandboxExecutionResult {
    val err = error
    val verdictValue = verdict
    return SandboxExecutionResult(
        requestId = UUID.randomUUID(),
        status = when (verdictValue) {
            Verdict.OK -> ru.sandbox.model.ExecutionStatus.SUCCESS
            Verdict.WRONG_ANSWER -> ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR
            Verdict.PRESENTATION_ERROR -> ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR
            Verdict.TIME_LIMIT_EXCEEDED -> ru.sandbox.model.ExecutionStatus.TIME_LIMIT_EXCEEDED
            Verdict.MEMORY_LIMIT_EXCEEDED -> ru.sandbox.model.ExecutionStatus.MEMORY_LIMIT_EXCEEDED
            Verdict.RUNTIME_ERROR -> ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR
            Verdict.COMPILATION_ERROR -> ru.sandbox.model.ExecutionStatus.COMPILATION_ERROR
        },
        output = output,
        error = err,
        executionTimeMs = executionTimeMs,
        memoryUsedKb = memoryUsedKb
    )
}

private fun ru.scenarioplayer.ScenarioResult.toWorkerScenarioResult(): ru.worker.model.ScenarioResult {
    return ru.worker.model.ScenarioResult(
        scenarioId = scenarioId,
        status = when (status) {
            "PASSED" -> TestStatus.PASSED
            "FAILED" -> TestStatus.FAILED
            "ERROR" -> TestStatus.ERROR
            else -> TestStatus.SKIPPED
        },
        stepResults = stepResults.map { step ->
            ru.worker.model.StepResult(
                stepNumber = step.stepNumber,
                status = when (step.status) {
                    "PASSED" -> TestStatus.PASSED
                    "FAILED" -> TestStatus.FAILED
                    "ERROR" -> TestStatus.ERROR
                    else -> TestStatus.SKIPPED
                },
                actualOutput = step.actualOutput,
                expectedOutput = step.expectedOutput,
                stateMatches = step.stateMatches
            )
        },
        finalState = finalState
    )
}
