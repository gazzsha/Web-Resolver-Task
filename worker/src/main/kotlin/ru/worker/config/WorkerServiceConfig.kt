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
import ru.scenarioplayer.DefaultScenarioRunnerImpl
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

    // F-21: wire the real DefaultScenarioRunnerImpl. The previous bean was an
    // anonymous stub that returned status="PASSED" for every step regardless of
    // code, language or input — silently bypassing scenario-based grading. A
    // student submitting a stub that prints fixed output would have earned full
    // marks for any task using scenarioTests.
    @Bean
    fun scenarioRunner(dockerSandboxService: DockerSandboxService): ScenarioRunner =
        DefaultScenarioRunnerImpl(dockerSandboxService)

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
