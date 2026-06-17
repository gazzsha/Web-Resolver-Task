package ru.sandbox.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import ru.sandbox.model.Verdict
import java.time.Duration

@Component
class SandboxMetrics(private val registry: MeterRegistry) {

    private val oomCounter: Counter = Counter.builder(OOM_TOTAL)
        .description("Sandbox container terminated by OOM killer")
        .register(registry)

    fun startExecutionTimer(): Timer.Sample = Timer.start(registry)

    fun stopExecutionTimer(sample: Timer.Sample, language: String, verdict: Verdict) {
        val normalizedLanguage = normalizeLanguage(language)
        val timer = Timer.builder(EXECUTION_SECONDS)
            .description("Sandbox execution wall-time")
            .tag("language", normalizedLanguage)
            .tag("verdict", verdict.name)
            .publishPercentileHistogram()
            .serviceLevelObjectives(
                Duration.ofSeconds(1),
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
            )
            .register(registry)
        sample.stop(timer)
    }

    fun recordOomKilled() {
        oomCounter.increment()
    }

    private fun normalizeLanguage(language: String): String =
        when (language.lowercase().trim()) {
            "java", "python", "kotlin" -> language.lowercase()
            else -> "unknown"
        }

    companion object {
        const val EXECUTION_SECONDS = "sandbox_execution_seconds"
        const val OOM_TOTAL = "sandbox_oom_killed_total"
    }
}
