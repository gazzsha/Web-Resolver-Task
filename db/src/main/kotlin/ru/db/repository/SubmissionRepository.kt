package ru.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.db.entity.SubmissionEntity
import java.util.UUID

@Repository
interface SubmissionRepository : JpaRepository<SubmissionEntity, UUID> {

    @Query("SELECT s FROM SubmissionEntity s WHERE s.id = :id")
    fun findByIdWithResult(@Param("id") id: UUID): SubmissionEntity?

    fun findByTaskId(taskId: UUID): List<SubmissionEntity>

    fun findByUserId(userId: UUID): List<SubmissionEntity>

    @Query("SELECT s FROM SubmissionEntity s WHERE s.taskId = :taskId AND s.userId = :userId ORDER BY s.createdAt DESC")
    fun findByTaskIdAndUserId(
        @Param("taskId") taskId: UUID,
        @Param("userId") userId: UUID
    ): List<SubmissionEntity>
}
