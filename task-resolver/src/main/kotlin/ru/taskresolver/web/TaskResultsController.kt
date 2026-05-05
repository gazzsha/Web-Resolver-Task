package ru.taskresolver.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.taskresolver.service.TaskResultSaveService
import java.util.*

@RestController
@RequestMapping("/api/v1/task-results")
class TaskResultsController(
    private val taskResultSaveService: TaskResultSaveService,
    private val objectMapper: ObjectMapper
) {

    @PostMapping
    fun saveResult(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, String>> {
        try {
            // Save result directly from request
            val submissionId = UUID.fromString(request["submissionId"] as String)
            val taskId = UUID.fromString(request["taskId"] as String)
            val testId = UUID.fromString(request["testId"] as String)
            val code = request["code"] as String
            val language = request["language"] as String
            val status = request["status"] as String
            val totalTests = (request["totalTests"] as Number).toInt()
            val passedTests = (request["passedTests"] as Number).toInt()
            val totalExecutionTimeMs = (request["totalExecutionTimeMs"] as Number).toLong()
            val memoryUsedKb = (request["memoryUsedKb"] as Number).toLong()

            @Suppress("UNCHECKED_CAST")
            val testResultsData = request["testResults"] as List<Map<String, Any>>

            val testResults = testResultsData.map { tr ->
                ru.taskresolver.service.TestExecutionResult(
                    testId = UUID.fromString(tr["testId"] as String),
                    status = ru.db.entity.TestStatus.valueOf(tr["status"] as String),
                    verdict = ru.db.entity.Verdict.valueOf(tr["verdict"] as String),
                    output = tr["output"] as? String,
                    error = tr["error"] as? String,
                    executionTimeMs = (tr["executionTimeMs"] as Number).toLong(),
                    memoryUsedKb = (tr["memoryUsedKb"] as Number).toLong()
                )
            }

            val result = taskResultSaveService.saveResult(
                submissionId = submissionId,
                taskId = taskId,
                testId = testId,
                code = code,
                language = language,
                testResults = testResults
            )

            return ResponseEntity.ok(mapOf("id" to result.id.toString(), "status" to "saved"))
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message!!))
        }
    }

    @GetMapping("/{submissionId}")
    fun getResultBySubmissionId(@PathVariable submissionId: String): ResponseEntity<Map<String, Any?>> {
        // Try to find by submissionId (UUID)
        val result = taskResultSaveService.getResultBySubmissionId(UUID.fromString(submissionId))

        if (result == null) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(mapOf(
            "id" to result.id.toString(),
            "submissionId" to result.submissionId.toString(),
            "taskId" to result.taskId.toString(),
            "testId" to result.testId.toString(),
            "status" to result.status.name,
            "totalTests" to result.totalTests,
            "passedTests" to result.passedTests,
            "totalExecutionTimeMs" to result.totalExecutionTimeMs,
            "memoryUsedKb" to result.memoryUsedKb,
            "language" to result.language,
            "testResults" to result.testResults.map { tr ->
                mapOf(
                    "testId" to tr.testId.toString(),
                    "status" to tr.status.name,
                    "verdict" to tr.verdict.name,
                    "output" to tr.output,
                    "error" to tr.error,
                    "executionTimeMs" to tr.executionTimeMs,
                    "memoryUsedKb" to tr.memoryUsedKb
                )
            },
            "createdAt" to result.createdAt.toString()
        ))
    }

    @GetMapping("/test/{testId}")
    fun getResultsByTestId(@PathVariable testId: UUID): ResponseEntity<List<Map<String, Any?>>> {
        val results = taskResultSaveService.getResultsByTestId(testId)

        val response = results.map { result ->
            mapOf(
                "id" to result.id.toString(),
                "submissionId" to result.submissionId.toString(),
                "taskId" to result.taskId.toString(),
                "testId" to result.testId.toString(),
                "status" to result.status.name,
                "totalTests" to result.totalTests,
                "passedTests" to result.passedTests,
                "totalExecutionTimeMs" to result.totalExecutionTimeMs,
                "memoryUsedKb" to result.memoryUsedKb,
                "language" to result.language,
                "testResults" to result.testResults.map { tr ->
                    mapOf(
                        "testId" to tr.testId.toString(),
                        "status" to tr.status.name,
                        "verdict" to tr.verdict.name,
                        "output" to tr.output,
                        "error" to tr.error,
                        "executionTimeMs" to tr.executionTimeMs,
                        "memoryUsedKb" to tr.memoryUsedKb
                    )
                },
                "createdAt" to result.createdAt.toString()
            )
        }

        return ResponseEntity.ok(response)
    }
}
