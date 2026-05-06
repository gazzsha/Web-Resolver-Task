package ru.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.db.entity.TaskResultEntity
import ru.worker.model.TaskStatus
import java.util.UUID

@Repository
interface TaskResultRepository : JpaRepository<TaskResultEntity, Long> {

    @Query("SELECT tr FROM TaskResultEntity tr WHERE tr.submissionId = :submissionId")
    fun findBySubmissionId(submissionId: UUID): TaskResultEntity?

    @Query("SELECT tr FROM TaskResultEntity tr WHERE tr.taskId = :taskId ORDER BY tr.createdAt DESC")
    fun findByTaskId(taskId: UUID): TaskResultEntity?

    fun findByTestId(testId: UUID): List<TaskResultEntity>

    fun findByUserId(userId: UUID): List<TaskResultEntity>

    fun findBySubmissionIdIn(submissionIds: List<UUID>): List<TaskResultEntity>

    @Query(
        "SELECT COUNT(DISTINCT tr.taskId) FROM TaskResultEntity tr " +
        "WHERE tr.submissionId IN :submissionIds AND tr.status = 'SUCCESS'"
    )
    fun countDistinctSolvedTasksBySubmissionIds(@Param("submissionIds") submissionIds: List<UUID>): Long

    @Query("SELECT tr FROM TaskResultEntity tr WHERE tr.testId = :testId AND tr.userId = :userId ORDER BY tr.createdAt DESC")
    fun findByTestIdAndUserId(
        @Param("testId") testId: UUID,
        @Param("userId") userId: UUID
    ): List<TaskResultEntity>

    @Query("SELECT tr FROM TaskResultEntity tr WHERE tr.status = :status ORDER BY tr.createdAt DESC")
    fun findByStatus(@Param("status") status: TaskStatus): List<TaskResultEntity>
}
