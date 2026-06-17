package ru.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/auth/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractBearerToken(request)
        if (token != null) {
            runCatching {
                val claims = jwtTokenProvider.parseAndValidate(token)
                if (claims.typ != "access") throw JwtException("Expected access token, got ${claims.typ}")

                val userId = UUID.fromString(claims.subject)
                val role = requireNotNull(claims.role) { "Role claim missing in access token" }
                val email = requireNotNull(claims.email) { "Email claim missing in access token" }

                val principal = UserPrincipal(id = userId, email = email, role = role)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
                val auth = UsernamePasswordAuthenticationToken(principal, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }.onFailure { ex ->
                when (ex) {
                    is JwtException -> logger.debug("JWT validation failed: ${ex.message}")
                    else -> logger.warn("Unexpected error during JWT processing: ${ex.message}")
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
    }
}
