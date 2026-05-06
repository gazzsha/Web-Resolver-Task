package ru.submission

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import ru.db.entity.CodeComplexity
import ru.taskresolver.service.AIAnalysisInput
import ru.taskresolver.service.TaskResultSaveService
import ru.taskresolver.service.TestExecutionResult
import ru.worker.model.TaskStatus
import ru.worker.model.TestStatus
import ru.worker.model.Verdict
import java.util.UUID

/**
 * Integration test for the full submission flow:
 *   register -> login -> saveResult (simulates Kafka worker, bypasses broker)
 *   -> GET /api/v1/task-results/{submissionId} returns persisted data.
 *
 * Exercises: PostgreSQL (Flyway), Spring context, JWT, JPA, DTO mapping, Security.
 * Kafka and Docker sandbox are excluded via spring.autoconfigure.exclude.
 *
 * Run with:
 *   RUN_INTEGRATION_TESTS=true ./gradlew :MainApplication:test \
 *     --tests "ru.submission.SubmissionFlowIntegrationTest"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
    named = "RUN_INTEGRATION_TESTS",
    matches = "true",
    disabledReason = "Requires Docker daemon for Testcontainers PostgreSQL. " +
        "Run manually with RUN_INTEGRATION_TESTS=true.",
)
class SubmissionFlowIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("web_resolver_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
            }
            registry.add("jwt.secret") { "test-jwt-secret-32-bytes-padded!!" }
            registry.add("jwt.access-ttl-minutes") { "60" }
            registry.add("jwt.refresh-ttl-days") { "7" }
            registry.add("gigachat.auth-key") { "" }
        }
    }

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    @Autowired
    lateinit var taskResultSaveService: TaskResultSaveService

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun register(email: String, password: String): String {
        val result = mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to password))),
        )
            .andExpect(status().isOk)
            .andReturn()
        return om.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun login(email: String, password: String): String {
        val result = mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to password))),
        )
            .andExpect(status().isOk)
            .andReturn()
        return om.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `submission result is persisted and retrievable via api after service save`() {
        val email = "submission-flow-${UUID.randomUUID()}@diplom.local"
        val password = "TestPass99!"

        // Step 1: register + login to get a valid JWT
        register(email, password)
        val accessToken = login(email, password)

        // Step 2: simulate the Kafka worker by calling TaskResultSaveService directly.
        // No Submission row is pre-created; saveResult handles the case where findById is empty.
        val submissionId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val testId = UUID.randomUUID()

        val testResult = TestExecutionResult(
            testId = UUID.randomUUID(),
            status = TestStatus.PASSED,
            verdict = Verdict.OK,
            output = "3",
            error = null,
            executionTimeMs = 42,
            memoryUsedKb = 1024,
        )

        val aiInput = AIAnalysisInput(
            codeQuality = 90,
            issues = emptyList(),
            recommendations = listOf("Looks clean"),
            explanation = "Rule-based: all tests passed, code is straightforward.",
            complexity = CodeComplexity.LOW,
            modelVersion = "rule-based-v1",
        )

        val saved = taskResultSaveService.saveResult(
            submissionId = submissionId,
            taskId = taskId,
            testId = testId,
            code = "class Solution { public int sum(int a, int b) { return a + b; } }",
            language = "JAVA",
            testResults = listOf(testResult),
            aiAnalysis = aiInput,
        )

        // Step 3: assert service-layer state
        assertNotNull(saved.id)
        assertEquals(TaskStatus.SUCCESS, saved.status)
        assertNotNull(saved.aiAnalysis)
        assertEquals(90, saved.aiAnalysis!!.codeQualityScore)

        // Step 4: GET /api/v1/task-results/{submissionId} with Bearer token
        mvc.perform(
            get("/api/v1/task-results/$submissionId")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.totalTests").value(1))
            .andExpect(jsonPath("$.passedTests").value(1))
            .andExpect(jsonPath("$.aiAnalysis.codeQuality").value(90))
            .andExpect(jsonPath("$.aiAnalysis.explanation").value("Rule-based: all tests passed, code is straightforward."))
            .andExpect(jsonPath("$.language").value("java"))
    }

    @Test
    fun `get task result without token returns 401`() {
        mvc.perform(get("/api/v1/task-results/${UUID.randomUUID()}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `get task result for unknown submission id returns 404`() {
        val email = "notfound-flow-${UUID.randomUUID()}@diplom.local"
        register(email, "TestPass99!")
        val token = login(email, "TestPass99!")

        mvc.perform(
            get("/api/v1/task-results/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isNotFound)
    }
}
