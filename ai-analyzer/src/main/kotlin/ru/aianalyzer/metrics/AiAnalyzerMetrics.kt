package ru.aianalyzer.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AiAnalyzerMetrics(private val registry: MeterRegistry) {

    fun recordCall(variant: String, outcome: String) {
        Counter.builder(CALLS_TOTAL)
            .description("Total AI analyzer calls grouped by variant and outcome")
            .tag("variant", variant)
            .tag("outcome", outcome)
            .register(registry)
            .increment()
    }

    fun recordSchemaValidation(valid: Boolean) {
        Counter.builder(SCHEMA_VALID_TOTAL)
            .description("AI analyzer JSON schema validation results")
            .tag("valid", valid.toString())
            .register(registry)
            .increment()
    }

    fun startLatencyTimer(): Timer.Sample = Timer.start(registry)

    fun stopLatencyTimer(sample: Timer.Sample, variant: String, outcome: String) {
        val timer = Timer.builder(LATENCY_SECONDS)
            .description("End-to-end AI analyzer latency")
            .tag("variant", variant)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .serviceLevelObjectives(
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
            )
            .register(registry)
        sample.stop(timer)
    }

    companion object {
        const val CALLS_TOTAL = "ai_analyzer_calls_total"
        const val LATENCY_SECONDS = "ai_analyzer_latency_seconds"
        const val SCHEMA_VALID_TOTAL = "ai_analyzer_schema_valid_total"

        const val VARIANT_GIGACHAT = "gigachat"
        const val VARIANT_AST_HYBRID = "ast-hybrid"
        const val VARIANT_RULE_BASED = "rule-based"

        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_SCHEMA_ERROR = "schema_error"
        const val OUTCOME_FALLBACK = "fallback"
        const val OUTCOME_CACHE_HIT = "cache_hit"
    }
}
