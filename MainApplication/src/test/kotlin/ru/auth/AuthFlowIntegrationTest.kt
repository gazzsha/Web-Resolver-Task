package ru.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
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
import java.util.UUID

/**
 * Integration test for the full authentication flow.
 *
 * Requires Docker (Testcontainers spins up a real PostgreSQL container).
 * Kafka is excluded via spring.autoconfigure.exclude so auth tests run without a broker.
 *
 * Run with:
 *   ./gradlew :MainApplication:test --tests "ru.auth.AuthFlowIntegrationTest"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.DisplayName::class)
@EnabledIfEnvironmentVariable(
    named = "RUN_INTEGRATION_TESTS",
    matches = "true",
    disabledReason = "Requires Docker daemon at /var/run/docker.sock (default Linux/CI). " +
        "On macOS Docker Desktop the socket is at ~/.docker/run/docker.sock — start with " +
        "'sudo ln -s ~/.docker/run/docker.sock /var/run/docker.sock' or run on Linux/CI. " +
        "Functionality is covered by ./e2e_smoke.sh against a live stack."
)
class AuthFlowIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("web_resolver_test")
            .withUsername("test")
            .withPassword("test")

        // Unique email shared across the few tests that need to reuse the same registered account.
        // Using a fixed suffix keeps tests independent from execution order while still testing
        // the duplicate-email scenario deterministically.
        private val SHARED_EMAIL = "flow-test-${UUID.randomUUID()}@diplom.local"
        private val SHARED_PASSWORD = "TestPass99!"

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            // Kafka is not needed for auth tests — exclude the autoconfiguration entirely.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
            }
            // Provide a deterministic 32-byte test secret so JwtTokenProvider does not fall back
            // to its insecure dev key, which would differ from what a real access token carries.
            registry.add("jwt.secret") { "test-jwt-secret-32-bytes-padded!!" }
            registry.add("jwt.access-ttl-minutes") { "60" }
            registry.add("jwt.refresh-ttl-days") { "7" }
            // GigaChat is irrelevant for auth; silence the missing-key warning.
            registry.add("gigachat.auth-key") { "" }
        }
    }

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun registerBody(email: String = SHARED_EMAIL, password: String = SHARED_PASSWORD): String =
        om.writeValueAsString(mapOf("email" to email, "password" to password))

    private fun loginBody(email: String = SHARED_EMAIL, password: String = SHARED_PASSWORD): String =
        om.writeValueAsString(mapOf("email" to email, "password" to password))

    /** Registers SHARED_EMAIL and returns the access token. Idempotent within one test run. */
    private fun registerAndGetAccess(): String {
        val result = mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody()),
        )
            .andExpect(status().isOk)
            .andReturn()
        return om.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    /** Logs in SHARED_EMAIL and returns the full response body as a JsonNode. */
    private fun loginAndGetResponse(): com.fasterxml.jackson.databind.JsonNode {
        val result = mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()),
        )
            .andExpect(status().isOk)
            .andReturn()
        return om.readTree(result.response.contentAsString)
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `register then login returns same user with valid jwt`() {
        val uniqueEmail = "register-login-${UUID.randomUUID()}@diplom.local"

        // Step 1 — register
        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to uniqueEmail, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.email").value(uniqueEmail))

        // Step 2 — login with same credentials
        mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to uniqueEmail, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.email").value(uniqueEmail))
    }

    @Test
    fun `register with duplicate email returns 409`() {
        val duplicateEmail = "dup-${UUID.randomUUID()}@diplom.local"

        // First registration must succeed.
        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to duplicateEmail, "password" to "Pass1234!"))),
        ).andExpect(status().isOk)

        // Second registration with the same email must be rejected.
        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to duplicateEmail, "password" to "Pass1234!"))),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = "wrongpw-${UUID.randomUUID()}@diplom.local"

        // Register first so the user exists.
        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to "CorrectPw1!"))),
        ).andExpect(status().isOk)

        // Attempt login with incorrect password.
        mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to "WrongPw999!"))),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint without token returns 401`() {
        mvc.perform(get("/api/v1/tasks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint with valid access token returns 200`() {
        val email = "access-ok-${UUID.randomUUID()}@diplom.local"

        val registerResult = mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andReturn()

        val accessToken = om.readTree(registerResult.response.contentAsString).get("accessToken").asText()

        mvc.perform(
            get("/api/v1/tasks")
                .header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `protected endpoint with malformed token returns 401`() {
        mvc.perform(
            get("/api/v1/tasks")
                .header("Authorization", "Bearer this.is.not.a.valid.jwt"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token returns new token pair`() {
        val email = "refresh-${UUID.randomUUID()}@diplom.local"

        val registerResult = mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to email, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andReturn()

        val tree = om.readTree(registerResult.response.contentAsString)
        val originalRefreshToken = tree.get("refreshToken").asText()
        val originalAccessToken = tree.get("accessToken").asText()

        // Small sleep so the issued-at timestamp differs, guaranteeing the new access token
        // is a distinct string even if token TTLs are the same.
        Thread.sleep(1_000)

        val refreshResult = mvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("refreshToken" to originalRefreshToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val newTree = om.readTree(refreshResult.response.contentAsString)
        val newAccessToken = newTree.get("accessToken").asText()

        // New access token must be different from the original (new iat).
        assert(newAccessToken != originalAccessToken) {
            "Expected a new access token after refresh, but got the same string"
        }
    }

    @Test
    fun `seed teacher user can login`() {
        // This test verifies that the Flyway V3 migration bcrypt hashes
        // produced by pgcrypto's blowfish are compatible with Spring's BCryptPasswordEncoder.
        // The teacher seed is inserted by V3__seed_users.sql when the test container starts
        // (Flyway runs automatically on first connect via spring.flyway.enabled=true).
        mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to "teacher@diplom.local", "password" to "Teacher123!"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.role").value("TEACHER"))
            .andExpect(jsonPath("$.email").value("teacher@diplom.local"))
    }
}
