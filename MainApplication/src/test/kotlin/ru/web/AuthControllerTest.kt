package ru.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.db.entity.UserEntity
import ru.db.entity.UserRole
import ru.security.JwtClaims
import ru.security.JwtTokenProvider
import ru.taskresolver.model.exception.EmailAlreadyTakenException
import ru.taskresolver.service.UserService
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [AuthController::class, AuthExceptionHandler::class])
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    @MockBean
    lateinit var userService: UserService

    @MockBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private val testUserId = UUID.randomUUID()
    private val testEmail = "unit-test@diplom.local"
    private val testAccess = "access.token.stub"
    private val testRefresh = "refresh.token.stub"

    private fun userEntity(
        id: UUID = testUserId,
        email: String = testEmail,
        role: UserRole = UserRole.STUDENT,
    ) = UserEntity(
        id = id,
        email = email,
        passwordHash = "hash",
        role = role,
        createdAt = Instant.now(),
    )

    // -------------------------------------------------------------------------
    // POST /auth/register
    // -------------------------------------------------------------------------

    @Test
    fun `register valid body returns 200 with jwt response`() {
        val user = userEntity()
        given(userService.register(anyString(), anyString(), eqNonNull(UserRole.STUDENT))).willReturn(user)
        given(jwtTokenProvider.generateAccess(testUserId, testEmail, UserRole.STUDENT)).willReturn(testAccess)
        given(jwtTokenProvider.generateRefresh(testUserId)).willReturn(testRefresh)

        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to testEmail, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value(testAccess))
            .andExpect(jsonPath("$.refreshToken").value(testRefresh))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.email").value(testEmail))

        verify(userService).register(anyString(), anyString(), eqNonNull(UserRole.STUDENT))
    }

    @Test
    fun `register with already taken email returns 409`() {
        given(userService.register(anyString(), anyString(), eqNonNull(UserRole.STUDENT)))
            .willThrow(EmailAlreadyTakenException("Email already taken: $testEmail"))

        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to testEmail, "password" to "Pass1234!"))),
        )
            .andExpect(status().isConflict)
    }

    // -------------------------------------------------------------------------
    // POST /auth/login
    // -------------------------------------------------------------------------

    @Test
    fun `login valid credentials returns 200`() {
        val user = userEntity()
        given(userService.authenticate(testEmail, "Pass1234!")).willReturn(user)
        given(jwtTokenProvider.generateAccess(testUserId, testEmail, UserRole.STUDENT)).willReturn(testAccess)
        given(jwtTokenProvider.generateRefresh(testUserId)).willReturn(testRefresh)

        mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to testEmail, "password" to "Pass1234!"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value(testAccess))

        verify(userService).authenticate(testEmail, "Pass1234!")
    }

    @Test
    fun `login wrong credentials returns 401`() {
        given(userService.authenticate(anyString(), anyString()))
            .willThrow(BadCredentialsException("Invalid credentials"))

        mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("email" to testEmail, "password" to "wrong!"))),
        )
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // POST /auth/refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh with valid refresh token returns 200 new pair`() {
        val refreshClaims = JwtClaims(
            subject = testUserId.toString(),
            typ = "refresh",
            email = null,
            role = null,
        )
        given(jwtTokenProvider.parseAndValidate("good.refresh.token")).willReturn(refreshClaims)
        given(userService.findById(testUserId)).willReturn(userEntity())
        given(jwtTokenProvider.generateAccess(testUserId, testEmail, UserRole.STUDENT)).willReturn("new.access")
        given(jwtTokenProvider.generateRefresh(testUserId)).willReturn("new.refresh")

        mvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("refreshToken" to "good.refresh.token"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new.access"))
            .andExpect(jsonPath("$.refreshToken").value("new.refresh"))
    }

    @Test
    fun `refresh with access token typ=access returns 401`() {
        val accessClaims = JwtClaims(
            subject = testUserId.toString(),
            typ = "access",
            email = testEmail,
            role = UserRole.STUDENT,
        )
        given(jwtTokenProvider.parseAndValidate("access.token.stub")).willReturn(accessClaims)

        mvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("refreshToken" to "access.token.stub"))),
        )
            .andExpect(status().isUnauthorized)
    }
}
