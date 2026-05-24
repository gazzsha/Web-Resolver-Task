package ru.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authorization.AuthorityAuthorizationManager
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    @Value("\${prometheus.scraper.username:prometheus-scraper}")
    private val scraperUser: String,
    // Fail-fast: must be supplied via PROMETHEUS_SCRAPER_PASSWORD env var.
    // No source-baked default — see DIFFERENTIAL_REVIEW_REPORT.md F-3.
    @Value("\${prometheus.scraper.password}")
    private val scraperPassword: String,
) {
    // Docker bridge networks (172.16/12), localhost IPv4/IPv6.
    private val allowedCidrs = listOf("172.16.0.0/12", "127.0.0.1/32", "::1")
    private val ipMatchers = allowedCidrs.map(::IpAddressMatcher)

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(10)

    @Bean
    fun prometheusScraperUsers(encoder: PasswordEncoder): UserDetailsService {
        val user = User.withUsername(scraperUser)
            .password(encoder.encode(scraperPassword))
            .roles("OPS")
            .build()
        return InMemoryUserDetailsManager(user)
    }

    // Defense-in-depth: запрос проходит только если (A) IP в Docker bridge / loopback
    // И (B) basic-auth попал в учётку с ролью OPS. Любой слой по отдельности недостаточен.
    //
    // F-1 fix: возвращаем AuthorizationDecision(false) вместо null на IP-deny.
    // Spring Security 6 AuthorizationFilter трактует null как «abstain» и пропускает
    // запрос дальше без AccessDeniedException — то есть null == grant, что инвертирует
    // защиту. См. DIFFERENTIAL_REVIEW_REPORT.md F-1.
    private fun prometheusAccess(): AuthorizationManager<RequestAuthorizationContext> {
        val hasOps = AuthorityAuthorizationManager.hasRole<RequestAuthorizationContext>("OPS")
        return AuthorizationManager { authentication, ctx ->
            val ipOk = ipMatchers.any { it.matches(ctx.request) }
            if (!ipOk) return@AuthorizationManager AuthorizationDecision(false)
            hasOps.check(authentication, ctx)
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/health",
                        "/actuator/info",
                        "/error",
                    ).permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .access(prometheusAccess())
                    .anyRequest().authenticated()
            }
            .httpBasic { }
            .exceptionHandling { eh ->
                eh.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("""{"errorMessage":"Unauthorized","errorDetails":{}}""")
                }
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration().apply {
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "http://127.0.0.1:*",
            )
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", cfg)
        return source
    }
}
