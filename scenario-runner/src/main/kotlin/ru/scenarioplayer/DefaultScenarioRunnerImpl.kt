package ru.scenarioplayer

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.model.*
import ru.sandbox.service.DockerSandboxService
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Scenario test for stateful/interactive tasks
 */
data class ScenarioTest(
    val scenarioId: UUID,
    val steps: List<ScenarioStep>
)

/**
 * Single step in scenario test
 */
data class ScenarioStep(
    val stepNumber: Int,
    val input: String,
    val expectedOutput: String? = null,
    val expectedStateChange: String? = null
)

/**
 * Result of scenario test execution
 */
data class ScenarioResult(
    val scenarioId: UUID,
    val status: String,
    val stepResults: List<StepResult>,
    val finalState: String?
)

/**
 * Result of single scenario step
 */
data class StepResult(
    val stepNumber: Int,
    val status: String,
    val actualOutput: String?,
    val expectedOutput: String?,
    val stateMatches: Boolean
)

/**
 * Scenario Runner interface
 */
interface ScenarioRunner {
    fun runScenario(code: String, language: String, scenario: ScenarioTest): ScenarioResult
    fun runStep(code: String, language: String, step: ScenarioStep, currentState: String?): StepResult
    fun extractState(output: String): String?
}

/**
 * Default implementation of Scenario Runner
 * 
 * Key feature for complex interactive tasks:
 * - Menu-based programs
 * - Multi-step interactions
 * - State-dependent behavior validation
 */
class DefaultScenarioRunnerImpl(
    private val sandboxService: DockerSandboxService
) : ScenarioRunner {
    
    override fun runScenario(code: String, language: String, scenario: ScenarioTest): ScenarioResult {
        logger.info { "Running scenario ${scenario.scenarioId} with ${scenario.steps.size} steps" }
        
        val stepResults = mutableListOf<StepResult>()
        var currentState: String? = null
        var allPassed = true
        
        for ((index, step) in scenario.steps.withIndex()) {
            logger.debug { "Executing step ${index + 1}/${scenario.steps.size}" }
            
            val stepResult = runStep(code, language, step, currentState)
            stepResults.add(stepResult)
            
            if (stepResult.status != "PASSED") {
                allPassed = false
                logger.warn { "Step ${index + 1} failed with status ${stepResult.status}" }
                break // Stop on first failure
            }
            
            // Update state for next step
            currentState = stepResult.actualOutput
        }
        
        val finalStatus = when {
            allPassed && stepResults.size == scenario.steps.size -> "PASSED"
            stepResults.any { it.status == "ERROR" } -> "ERROR"
            else -> "FAILED"
        }
        
        return ScenarioResult(
            scenarioId = scenario.scenarioId,
            status = finalStatus,
            stepResults = stepResults,
            finalState = currentState
        )
    }
    
    override fun runStep(
        code: String,
        language: String,
        step: ScenarioStep,
        currentState: String?
    ): StepResult {
        logger.debug { "Running scenario step ${step.stepNumber}" }
        
        try {
            // Build cumulative input for stateful execution
            val input = buildInputForStep(step, currentState)
            
            val sandboxRequest = SandboxExecutionRequest(
                requestId = UUID.randomUUID(),
                code = code,
                language = language,
                className = "Solution",
                testInput = input,
                expectedOutput = step.expectedOutput ?: "",
                timeoutSeconds = 10, // Scenarios may take longer
                memoryLimitMb = 256,
                cpuLimit = 1.0
            )
            
            val sandboxResult = sandboxService.execute(sandboxRequest)
            
            val status = when (sandboxResult.status) {
                ExecutionStatus.SUCCESS -> {
                    // Check if output matches expected
                    if (step.expectedOutput == null || 
                        sandboxResult.output?.contains(step.expectedOutput, ignoreCase = true) == true) {
                        "PASSED"
                    } else {
                        "FAILED"
                    }
                }
                ExecutionStatus.COMPILATION_ERROR -> "FAILED"
                ExecutionStatus.TIME_LIMIT_EXCEEDED -> "FAILED"
                ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> "FAILED"
                ExecutionStatus.RUNTIME_ERROR -> "FAILED"
                ExecutionStatus.SECURITY_VIOLATION -> "FAILED"
                ExecutionStatus.INTERNAL_ERROR -> "ERROR"
            }
            
            // Check if state matches expected
            val stateMatches = if (step.expectedStateChange != null) {
                val newState = extractState(sandboxResult.output ?: "")
                newState?.contains(step.expectedStateChange, ignoreCase = true) ?: false
            } else {
                true
            }
            
            return StepResult(
                stepNumber = step.stepNumber,
                status = status,
                actualOutput = sandboxResult.output,
                expectedOutput = step.expectedOutput,
                stateMatches = stateMatches
            )
        } catch (e: Exception) {
            logger.error(e) { "Scenario step ${step.stepNumber} failed with exception" }
            
            return StepResult(
                stepNumber = step.stepNumber,
                status = "ERROR",
                actualOutput = null,
                expectedOutput = step.expectedOutput,
                stateMatches = false
            )
        }
    }
    
    override fun extractState(output: String): String? {
        // Extract state from output using markers
        // Format: [STATE: state_name]
        val statePattern = Regex("""\[STATE:\s*([^\]]+)\]""")
        return statePattern.find(output)?.groupValues?.get(1)
    }
    
    /**
     * Build cumulative input for stateful execution
     */
    private fun buildInputForStep(step: ScenarioStep, previousState: String?): String {
        return if (previousState != null) {
            // Include state context
            "$previousState\n${step.input}"
        } else {
            step.input
        }
    }
}
