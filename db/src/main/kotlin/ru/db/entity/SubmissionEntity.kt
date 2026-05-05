package ru.db.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Entity for tracking code submissions
 * Each submission represents a single attempt to solve a task
 */
@Entity
@Table(name = "submissions")
data class SubmissionEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "task_id", nullable = false)
    var taskId: UUID,

    @Column(name = "test_id", nullable = false)
    var testId: UUID,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "code", nullable = false, columnDefinition = "TEXT")
    var code: String,

    @Column(name = "language", nullable = false)
    var language: String,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SubmissionStatus = SubmissionStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class SubmissionStatus {
    PENDING,      // Submission created, waiting for processing
    PROCESSING,   // Currently being executed
    COMPLETED,    // Processing finished, results available
    FAILED        // Processing failed
}
