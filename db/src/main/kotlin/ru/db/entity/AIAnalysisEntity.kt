package ru.db.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Entity for storing AI analysis results
 */
@Entity
@Table(name = "ai_analysis")
data class AIAnalysisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    
    @Column(name = "task_result_id", nullable = false, unique = true)
    var taskResultId: Long,
    
    @Column(name = "code_quality_score")
    var codeQualityScore: Int = 0,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues", columnDefinition = "jsonb")
    var issues: List<CodeIssueJson> = emptyList(),
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    var recommendations: List<String> = emptyList(),
    
    @Column(name = "explanation", columnDefinition = "TEXT")
    var explanation: String = "",
    
    @Column(name = "complexity")
    @Enumerated(EnumType.STRING)
    var complexity: CodeComplexity = CodeComplexity.MEDIUM,
    
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    
    @Column(name = "model_version")
    var modelVersion: String = "unknown"
)

enum class CodeComplexity {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

/**
 * JSON representation of code issue
 */
data class CodeIssueJson(
    val type: IssueType,
    val severity: Severity,
    val line: Int?,
    val message: String,
    val suggestion: String
)

enum class IssueType {
    BUG,
    CODE_SMELL,
    SECURITY,
    PERFORMANCE,
    STYLE
}

enum class Severity {
    BLOCKER,
    CRITICAL,
    MAJOR,
    MINOR,
    INFO
}
