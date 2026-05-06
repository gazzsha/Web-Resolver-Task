package ru.taskresolver.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.db.repository.AIAnalysisRepository
import ru.db.repository.SubmissionRepository
import ru.db.repository.TaskResultRepository
import ru.taskresolver.repository.jpa.repository.TestRepository
import ru.taskresolver.web.SubmissionSummary
import ru.taskresolver.web.UserStats
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class UserStatsService(
    private val submissionRepository: SubmissionRepository,
    private val taskResultRepository: TaskResultRepository,
    private val testRepository: TestRepository,
    private val aiAnalysisRepository: AIAnalysisRepository,
) {

    @Transactional(readOnly = true)
    fun getSubmissions(userId: UUID, limit: Int = 50): List<SubmissionSummary> {
        val submissions = submissionRepository.findByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(0, limit),
        )
        log.debug { "Fetched ${submissions.size} submissions for user $userId" }
        return submissions.map { s -> toSummary(s.id, s.taskId, s.language, s.status.name, s.createdAt) }
    }

    @Transactional(readOnly = true)
    fun getStats(userId: UUID): UserStats {
        val totalSubmissions = submissionRepository.countByUserId(userId)

        val submissions = submissionRepository.findByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(0, Int.MAX_VALUE),
        )
        val submissionIds = submissions.map { it.id }

        val tasksSolved = if (submissionIds.isEmpty()) {
            0L
        } else {
            taskResultRepository.countDistinctSolvedTasksBySubmissionIds(submissionIds)
        }

        val averageQuality = if (submissionIds.isEmpty()) {
            null
        } else {
            val taskResults = taskResultRepository.findBySubmissionIdIn(submissionIds)
            val taskResultIds = taskResults.map { it.id }
            if (taskResultIds.isEmpty()) null
            else aiAnalysisRepository.avgQualityScoreByTaskResultIds(taskResultIds)
        }

        val recentSubmissions = submissions.take(5)
            .map { s -> toSummary(s.id, s.taskId, s.language, s.status.name, s.createdAt) }

        log.debug { "Stats for user $userId: solved=$tasksSolved, total=$totalSubmissions, avgQuality=$averageQuality" }

        return UserStats(
            tasksSolved = tasksSolved,
            totalSubmissions = totalSubmissions,
            averageQuality = averageQuality,
            recentSubmissions = recentSubmissions,
        )
    }

    private fun toSummary(
        submissionId: UUID,
        taskId: UUID,
        language: String,
        submissionStatus: String,
        createdAt: java.time.Instant,
    ): SubmissionSummary {
        val taskResult = taskResultRepository.findBySubmissionId(submissionId)
        val taskTitle = testRepository.findTestByTestId(taskId)?.title
        val passedTests = taskResult?.passedTests
        val totalTests = taskResult?.totalTests
        val status = taskResult?.status?.name ?: submissionStatus

        return SubmissionSummary(
            id = submissionId,
            taskId = taskId,
            taskTitle = taskTitle,
            status = status,
            language = language,
            passedTests = passedTests,
            totalTests = totalTests,
            createdAt = createdAt,
        )
    }
}
