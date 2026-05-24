package ru.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.Base64

/**
 * Covers DIFFERENTIAL_REVIEW_REPORT.md findings F-1 / F-2 / F-3.
 *
 * Uses MockMvc with `request.setRemoteAddr(...)` because TestRestTemplate connects
 * from 127.0.0.1 only and cannot exercise the IP-blocked branch of
 * `SecurityConfig.prometheusAccess()`.
 *
 * Gated by RUN_INTEGRATION_TESTS=true (Testcontainers + Docker daemon required).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
    named = "RUN_INTEGRATION_TESTS",
    matches = "true",
    disabledReason = "Requires Docker daemon. Set RUN_INTEGRATION_TESTS=true to enable.",
)
class SecurityConfigTest {

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

        private fun basicAuth(user: String, pwd: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$user:$pwd".toByteArray())
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    /**
     * F-1 regression — non-loopback source IP MUST be rejected even when
     * basic-auth credentials are correct. Before the fix, `prometheusAccess()`
     * returned `null` on IP-block, which Spring Security 6's AuthorizationFilter
     * treats as abstain → grant.
     */
    @Test
    fun `prometheus endpoint rejects non-loopback source IP even with valid auth`() {
        mockMvc.perform(
            get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(SCRAPER_USER, SCRAPER_PASSWORD))
                .with { req ->
                    req.remoteAddr = "8.8.8.8"
                    req
                },
        ).andExpect(status().isForbidden)
    }

    /**
     * F-1 paired check — loopback IP without credentials returns 401, proving
     * the IP-allowlist alone is not enough (basic-auth still required).
     */
    @Test
    fun `prometheus endpoint rejects unauthenticated loopback request`() {
        mockMvc.perform(
            get("/actuator/prometheus")
                .with { req ->
                    req.remoteAddr = "127.0.0.1"
                    req
                },
        ).andExpect(status().isUnauthorized)
    }

    /**
     * F-1 happy path — loopback + correct credentials reach the endpoint.
     */
    @Test
    fun `prometheus endpoint accepts loopback request with scraper credentials`() {
        mockMvc.perform(
            get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(SCRAPER_USER, SCRAPER_PASSWORD))
                .with { req ->
                    req.remoteAddr = "127.0.0.1"
                    req
                },
        ).andExpect(status().isOk)
    }

    /**
     * F-2 regression — /actuator/metrics must NOT be exposed. Endpoint was
     * previously included in management.endpoints.web.exposure.include and
     * reachable by any authenticated student JWT, leaking meter names.
     * After the fix, only health/info/prometheus are exposed → /metrics is 404.
     */
    @Test
    fun `actuator metrics endpoint is not exposed`() {
        mockMvc.perform(
            get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(SCRAPER_USER, SCRAPER_PASSWORD))
                .with { req ->
                    req.remoteAddr = "127.0.0.1"
                    req
                },
        ).andExpect(status().isNotFound)
    }

    /**
     * Wrong basic-auth password from loopback returns 401 (not 200, not 403).
     */
    @Test
    fun `prometheus endpoint rejects wrong scraper password`() {
        mockMvc.perform(
            get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(SCRAPER_USER, "wrong-password"))
                .with { req ->
                    req.remoteAddr = "127.0.0.1"
                    req
                },
        ).andExpect(status().isUnauthorized)
    }
}
