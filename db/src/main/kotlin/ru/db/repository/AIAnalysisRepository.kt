package ru.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.db.entity.AIAnalysisEntity

@Repository
interface AIAnalysisRepository : JpaRepository<AIAnalysisEntity, Long> {

    fun findByTaskResultId(taskResultId: Long): AIAnalysisEntity?

    fun findByTaskResultIdIn(taskResultIds: List<Long>): List<AIAnalysisEntity>

    @Query(
        "SELECT AVG(a.codeQualityScore) FROM AIAnalysisEntity a " +
        "WHERE a.taskResultId IN :taskResultIds AND a.modelVersion <> 'rule-based'"
    )
    fun avgQualityScoreByTaskResultIds(@Param("taskResultIds") taskResultIds: List<Long>): Double?
}
