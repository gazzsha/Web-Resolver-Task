package ru.web

import io.jsonwebtoken.JwtException
import model.JwtResponse
import model.LoginRequest
import model.RefreshRequest
import model.RegisterRequest
import model.Role
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.server.ResponseStatusException
import ru.db.entity.UserRole
import ru.security.JwtTokenProvider
import ru.taskresolver.service.UserService
import web.AuthApi
import java.util.UUID

private val logger = LoggerFactory.getLogger(AuthController::class.java)

@Controller
class AuthController(
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider,
) : AuthApi {

    override fun register(registerRequest: RegisterRequest): ResponseEntity<JwtResponse> {
        val user = userService.register(
            email = registerRequest.email,
            rawPassword = registerRequest.password,
            role = UserRole.STUDENT,
        )
        logger.info("New user registered: ${user.email}")
        val access = jwtTokenProvider.generateAccess(user.id, user.email, user.role)
        val refresh = jwtTokenProvider.generateRefresh(user.id)
        return ResponseEntity.ok(JwtResponse(access, refresh, Role.STUDENT, user.email))
    }

    override fun login(loginRequest: LoginRequest): ResponseEntity<JwtResponse> {
        val user = userService.authenticate(loginRequest.email, loginRequest.password)
        val access = jwtTokenProvider.generateAccess(user.id, user.email, user.role)
        val refresh = jwtTokenProvider.generateRefresh(user.id)
        val role = Role.valueOf(user.role.name)
        return ResponseEntity.ok(JwtResponse(access, refresh, role, user.email))
    }

    override fun refresh(refreshRequest: RefreshRequest): ResponseEntity<JwtResponse> {
        val claims = try {
            jwtTokenProvider.parseAndValidate(refreshRequest.refreshToken)
        } catch (ex: JwtException) {
            logger.warn("Refresh token validation failed: ${ex.message}")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")
        }

        if (claims.typ != "refresh") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expected refresh token")
        }

        val userId = runCatching { UUID.fromString(claims.subject) }.getOrElse {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject")
        }

        val user = userService.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")

        val access = jwtTokenProvider.generateAccess(user.id, user.email, user.role)
        val refresh = jwtTokenProvider.generateRefresh(user.id)
        val role = Role.valueOf(user.role.name)
        return ResponseEntity.ok(JwtResponse(access, refresh, role, user.email))
    }
}
