package ru.worker.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import ru.worker.model.TaskStatus
import java.time.Duration

@Component
class WorkerMetrics(private val registry: MeterRegistry) {

    private val processingTimer: Timer = Timer.builder(PROCESSING_SECONDS)
        .description("Worker processTask end-to-end latency")
        .publishPercentileHistogram()
        .serviceLevelObjectives(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(60),
        )
        .register(registry)

    fun startProcessingTimer(): Timer.Sample = Timer.start(registry)

    fun stopProcessingTimer(sample: Timer.Sample) {
        sample.stop(processingTimer)
    }

    fun recordSubmission(status: TaskStatus) {
        Counter.builder(SUBMISSIONS_TOTAL)
            .description("Worker submissions grouped by final task status")
            .tag("status", status.name)
            .register(registry)
            .increment()
    }

    companion object {
        const val SUBMISSIONS_TOTAL = "worker_submissions_total"
        const val PROCESSING_SECONDS = "worker_processing_seconds"
    }
}
