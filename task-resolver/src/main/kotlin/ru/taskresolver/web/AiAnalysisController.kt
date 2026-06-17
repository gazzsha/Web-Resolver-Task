package ru.taskresolver.web

import model.AIAnalysisResponse
import model.ExplainErrorRequest
import model.ExplainErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import ru.aianalyzer.service.AIAnalyzer
import ru.taskresolver.service.TaskResultService
import web.AiAnalysisApi
import java.util.UUID

@Controller
class AiAnalysisController(
    private val taskResultService: TaskResultService,
    private val aiAnalyzer: AIAnalyzer
) : AiAnalysisApi {

    override fun getAIAnalysis(submissionId: UUID): ResponseEntity<AIAnalysisResponse> {
        val response = taskResultService.getAIAnalysis(submissionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response)
    }

    override fun explainError(submissionId: UUID, explainErrorRequest: ExplainErrorRequest): ResponseEntity<ExplainErrorResponse> {
        val taskResult = taskResultService.getTaskEntityBySubmissionId(submissionId)
            ?: return ResponseEntity.notFound().build()

        val explanation = aiAnalyzer.explainError(
            code = taskResult.code,
            language = taskResult.language,
            error = explainErrorRequest.error,
            testInput = explainErrorRequest.testInput
        )

        val response = ExplainErrorResponse()
            .errorType("Runtime Error")
            .explanation(explanation)
            .fixSuggestions(emptyList())

        return ResponseEntity.ok(response)
    }
}
