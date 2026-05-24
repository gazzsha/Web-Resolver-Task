package ru.worker.config

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Component
import ru.worker.metrics.WorkerMetrics
import ru.worker.service.*
import ru.aianalyzer.service.AIAnalyzer
import ru.scenarioplayer.ScenarioRunner
import ru.sandbox.metrics.SandboxMetrics
import ru.sandbox.service.DockerSandboxService
import ru.sandbox.service.SandboxImageManager

/**
 * Worker service configuration with Docker Sandbox support
 */
@Configuration
@EnableAsync
class WorkerServiceConfig {

    @Bean
    fun sandboxImageManager(): SandboxImageManager = SandboxImageManager()

    @Bean
    fun dockerSandboxService(im: SandboxImageManager, sandboxMetrics: SandboxMetrics): DockerSandboxService =
        DockerSandboxService(im, sandboxMetrics)

    @Bean
    fun testEngine(dockerSandboxService: DockerSandboxService): TestEngine {
        // Use Docker-based test engine for real code execution
        return DockerTestEngine(dockerSandboxService)
    }

    @Bean
    fun scenarioRunner(): ScenarioRunner {
        return object : ScenarioRunner {
            override fun runScenario(code: String, language: String, scenario: ru.scenarioplayer.ScenarioTest): ru.scenarioplayer.ScenarioResult {
                return ru.scenarioplayer.ScenarioResult(
                    scenarioId = scenario.scenarioId,
                    status = "PASSED",
                    stepResults = scenario.steps.map { step ->
                        ru.scenarioplayer.StepResult(
                            stepNumber = step.stepNumber,
                            status = "PASSED",
                            actualOutput = "Mock output",
                            expectedOutput = step.expectedOutput,
                            stateMatches = true
                        )
                    },
                    finalState = "completed"
                )
            }

            override fun runStep(code: String, language: String, step: ru.scenarioplayer.ScenarioStep, currentState: String?): ru.scenarioplayer.StepResult {
                return ru.scenarioplayer.StepResult(
                    stepNumber = step.stepNumber,
                    status = "PASSED",
                    actualOutput = "Mock output",
                    expectedOutput = step.expectedOutput,
                    stateMatches = true
                )
            }

            override fun extractState(output: String): String? {
                return output
            }
        }
    }

    @Bean
    fun workerService(
        testEngine: TestEngine,
        scenarioRunner: ScenarioRunner,
        aiAnalyzer: AIAnalyzer,
        workerMetrics: WorkerMetrics
    ): WorkerService {
        return WorkerService(
            testEngine = testEngine,
            scenarioRunner = scenarioRunner,
            aiAnalyzer = aiAnalyzer,
            metrics = workerMetrics
        )
    }
}

@Component
class SandboxImagePrewarmer(private val imageManager: SandboxImageManager) {

    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        imageManager.prewarm()
    }
}
