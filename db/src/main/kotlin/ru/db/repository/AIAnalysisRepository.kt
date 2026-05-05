package ru.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.db.entity.AIAnalysisEntity

@Repository
interface AIAnalysisRepository : JpaRepository<AIAnalysisEntity, Long> {
    
    fun findByTaskResultId(taskResultId: Long): AIAnalysisEntity?
}
