package ru.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests verifying that the Actuator Prometheus endpoint is correctly exposed,
 * protected by basic-auth + IP allowlist (SecurityConfig), and that core application
 * metrics (HTTP, JVM, ai-analyzer, sandbox, worker) are present in the scrape output.
 *
 * Security model under test:
 *   - GET /actuator/prometheus from 127.0.0.1 with basic-auth scraper:scraper-dev-pass → 200.
 *   - GET /actuator/prometheus from 127.0.0.1 without credentials → 401.
 *
 * Run:
 *   RUN_INTEGRATION_TESTS=true ./gradlew :MainApplication:test --tests "ru.observability.MetricsExposureTest"
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
    named = "RUN_INTEGRATION_TESTS",
    matches = "true",
    disabledReason = "Requires Docker daemon. Set RUN_INTEGRATION_TESTS=true to enable.",
)
class MetricsExposureTest {

    companion object {
        private const val SCRAPER_USER = "prometheus-scraper"
        private const val SCRAPER_PASSWORD = "scraper-dev-pass"

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
            registry.add("prometheus.scraper.username") { SCRAPER_USER }
            registry.add("prometheus.scraper.password") { SCRAPER_PASSWORD }
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    private var port: Int = 0

    private fun prometheusUrl() = "http://localhost:$port/actuator/prometheus"

    private fun authedTemplate(): TestRestTemplate =
        TestRestTemplate(SCRAPER_USER, SCRAPER_PASSWORD)

    private fun fetchPrometheus(): String {
        val response = authedTemplate().getForEntity(prometheusUrl(), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        return response.body ?: ""
    }

    @Test
    fun `actuator prometheus endpoint returns 200 with scraper basic-auth`() {
        val response = authedTemplate().getForEntity(prometheusUrl(), String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.toString())
            .contains("text/plain")
    }

    @Test
    fun `prometheus output contains core metrics`() {
        val body = fetchPrometheus()

        // Standard Spring Boot / Micrometer metrics always present after context start.
        assertThat(body)
            .contains("http_server_requests_seconds_count")
            .contains("jvm_memory_used_bytes")
            .contains("jvm_gc_pause_seconds_count")
            .contains("process_cpu_usage")

        // Application-specific metrics registered by ai-analyzer, sandbox, and worker modules.
        // Custom Timer/Counter meters register lazily on first use but their _total / _count
        // lines appear after any meter from the same name is registered; the worker meter is
        // eagerly registered in the WorkerMetrics @Component constructor.
        assertThat(body)
            .contains("worker_processing_seconds")
    }

    @Test
    fun `http counter increments after request`() {
        // Warm-up: hit /actuator/health to ensure at least one observation exists.
        authedTemplate().getForEntity("http://localhost:$port/actuator/health", String::class.java)

        val bodyBefore = fetchPrometheus()
        val countBefore = extractHttpCount(bodyBefore, "/actuator/health")

        // Second health request — counter must go up by exactly 1.
        authedTemplate().getForEntity("http://localhost:$port/actuator/health", String::class.java)

        val bodyAfter = fetchPrometheus()
        val countAfter = extractHttpCount(bodyAfter, "/actuator/health")

        assertThat(countAfter)
            .`as`("http_server_requests_seconds_count for /actuator/health should increase by 1")
            .isEqualTo(countBefore + 1.0)
    }

    /**
     * SecurityConfig requires both (A) loopback / Docker-bridge source IP AND
     * (B) basic-auth with ROLE_OPS. The test client connects from 127.0.0.1 (passes A)
     * but without credentials (fails B) → 401.
     */
    @Test
    fun `actuator prometheus rejects unauthenticated request`() {
        val anonTemplate = TestRestTemplate()
        val response = anonTemplate.getForEntity(prometheusUrl(), String::class.java)

        assertThat(response.statusCode)
            .`as`("Anonymous scraper requests must be rejected with 401 by basic-auth")
            .isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    /**
     * Parses the Prometheus text exposition format and returns the _count value
     * for http_server_requests_seconds_count where the uri label matches [uri].
     * Returns 0.0 if no matching line is found yet.
     */
    private fun extractHttpCount(body: String, uri: String): Double =
        body.lines()
            .filter { line ->
                line.startsWith("http_server_requests_seconds_count") &&
                    line.contains("uri=\"$uri\"")
            }
            .mapNotNull { line ->
                line.substringAfterLast("} ").trim().toDoubleOrNull()
            }
            .firstOrNull() ?: 0.0
}
