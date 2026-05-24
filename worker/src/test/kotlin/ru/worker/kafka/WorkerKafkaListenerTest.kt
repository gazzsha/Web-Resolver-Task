package ru.worker.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import ru.worker.model.TaskStatus
import ru.worker.model.WorkerTaskMessage
import ru.worker.model.WorkerTaskResult
import ru.worker.service.WorkerService
import java.util.UUID

/**
 * Listener-level tests for [WorkerKafkaListener].
 *
 * F-16: exceptions MUST NOT be caught by the listener — they must propagate
 * to the container's [DefaultErrorHandler] (which routes exhausted retries
 * to the DLT). The previous implementation had a try/catch that always
 * called acknowledge(), silently swallowing every failure.
 *
 * F-17: payloads above the code-length cap MUST be rejected before
 * deserialisation reaches WorkerService.
 */
class WorkerKafkaListenerTest {

    private val workerService = mockk<WorkerService>()
    private val resultPublisher = mockk<WorkerResultPublisher>(relaxed = true)
    private val acknowledgment = mockk<Acknowledgment>(relaxed = true)
    private val listener = WorkerKafkaListener(workerService, resultPublisher)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private fun record(value: String?, topic: String = "task-execution"): ConsumerRecord<String, String> =
        ConsumerRecord(topic, 0, 0L, "key", value)

    private fun sampleMessage(codeSize: Int = 100): WorkerTaskMessage = WorkerTaskMessage(
        taskId = UUID.randomUUID(),
        testId = UUID.randomUUID(),
        code = "x".repeat(codeSize),
        language = "python",
        testCases = emptyList(),
    )

    // ───────────────────────── F-16 regression ────────────────────────────────

    @Test
    fun `listener does not swallow exceptions from workerService - propagates to error handler`() {
        val msg = sampleMessage()
        every { workerService.processTask(any()) } throws RuntimeException("docker daemon down")

        val ex = assertThrows(RuntimeException::class.java) {
            listener.listenTask(record(objectMapper.writeValueAsString(msg)), acknowledgment)
        }
        assertEquals("docker daemon down", ex.message) {
            "F-16 regression: exception must propagate, not be swallowed by a try/catch"
        }
        // Crucially: acknowledge() MUST NOT be called on exception path —
        // that's what lets the DefaultErrorHandler retry + DLT route work.
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    @Test
    fun `listener does not swallow exceptions from result publisher`() {
        val msg = sampleMessage()
        every { workerService.processTask(any()) } returns dummyResult(msg)
        every { resultPublisher.publishResult(any()) } throws RuntimeException("kafka unreachable")

        assertThrows(RuntimeException::class.java) {
            listener.listenTask(record(objectMapper.writeValueAsString(msg)), acknowledgment)
        }
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    // ───────────────────────── F-17 regression ────────────────────────────────

    @Test
    fun `listener rejects raw kafka payload exceeding 2x MAX_CODE_LENGTH before parsing`() {
        // A huge envelope — well above MAX_CODE_LENGTH * 2. Should fail the
        // raw-payload guard BEFORE Jackson is involved.
        val oversizedRaw = "{\"junk\":\"" + "x".repeat(WorkerKafkaListener.MAX_CODE_LENGTH * 3) + "\"}"

        val ex = assertThrows(IllegalArgumentException::class.java) {
            listener.listenTask(record(oversizedRaw), acknowledgment)
        }
        assertTrue(ex.message!!.contains("payload too large")) { "Expected payload-size rejection, got: ${ex.message}" }
        verify(exactly = 0) { workerService.processTask(any()) }
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    @Test
    fun `listener rejects WorkerTaskMessage with code exceeding MAX_CODE_LENGTH`() {
        // Raw envelope size is fine (~70 KB JSON for a 65 KB code field), but
        // the parsed code field exceeds the per-message cap by 1 character.
        val msg = sampleMessage(codeSize = WorkerKafkaListener.MAX_CODE_LENGTH + 1)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            listener.listenTask(record(objectMapper.writeValueAsString(msg)), acknowledgment)
        }
        assertTrue(ex.message!!.contains("Submission code too large")) { "Got: ${ex.message}" }
        verify(exactly = 0) { workerService.processTask(any()) }
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    // ───────────────────────── Happy path ─────────────────────────────────────

    @Test
    fun `listener acknowledges only after successful processing and publishing`() {
        val msg = sampleMessage()
        every { workerService.processTask(any()) } returns dummyResult(msg)
        every { resultPublisher.publishResult(any()) } returns Unit

        listener.listenTask(record(objectMapper.writeValueAsString(msg)), acknowledgment)

        verify(exactly = 1) { workerService.processTask(any()) }
        verify(exactly = 1) { resultPublisher.publishResult(any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    private fun dummyResult(msg: WorkerTaskMessage) = WorkerTaskResult(
        taskId = msg.taskId,
        testId = msg.testId,
        code = msg.code,
        language = msg.language,
        status = TaskStatus.SUCCESS,
        testResults = emptyList(),
        scenarioResults = emptyList(),
        aiAnalysis = null,
        totalExecutionTimeMs = 100,
        memoryUsedKb = 0,
    )
}
