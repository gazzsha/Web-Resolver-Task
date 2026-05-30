package ru.taskresolver.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.taskresolver.service.UserStatsService
import java.time.Instant
import java.util.UUID

data class SubmissionSummary(
    val id: UUID,
    val taskId: UUID,
    val taskTitle: String?,
    val status: String,
    val language: String,
    val passedTests: Int?,
    val totalTests: Int?,
    val createdAt: Instant,
)

data class UserStats(
    val tasksSolved: Long,
    val totalSubmissions: Long,
    val averageQuality: Double?,
    val recentSubmissions: List<SubmissionSummary>,
)

@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val statsService: UserStatsService,
) {

    @GetMapping("/submissions")
    fun submissions(authentication: Authentication): ResponseEntity<List<SubmissionSummary>> {
        val userId = extractUserId(authentication)
        val result = statsService.getSubmissions(userId)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/stats")
    fun stats(authentication: Authentication): ResponseEntity<UserStats> {
        val userId = extractUserId(authentication)
        val result = statsService.getStats(userId)
        return ResponseEntity.ok(result)
    }

    /**
     * Extracts UUID from the UserPrincipal stored in the authentication context.
     * UserPrincipal lives in MainApplication; accessed via reflection to avoid a circular module dependency.
     */
    private fun extractUserId(authentication: Authentication): UUID {
        val principal = authentication.principal
        return try {
            val field = principal.javaClass.getDeclaredField("id").apply { isAccessible = true }
            field.get(principal) as UUID
        } catch (e: Exception) {
            throw IllegalStateException("Cannot extract user id from principal ${principal.javaClass.name}", e)
        }
    }
}
