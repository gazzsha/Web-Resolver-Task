package ru.taskresolver.web

import model.TaskResultResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import ru.taskresolver.service.TaskResultService
import web.TaskResultsApi
import java.util.UUID

@Controller
class TaskResultsController(
    private val taskResultService: TaskResultService
) : TaskResultsApi {

    override fun getTaskResult(submissionId: UUID): ResponseEntity<TaskResultResponse> {
        val result = taskResultService.getTaskResult(submissionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    override fun getTestResults(testId: UUID): ResponseEntity<List<TaskResultResponse>> {
        val results = taskResultService.getTestResults(testId)
        return ResponseEntity.ok(results)
    }
}
