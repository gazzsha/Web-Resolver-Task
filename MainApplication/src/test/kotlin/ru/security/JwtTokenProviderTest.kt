package ru.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.db.entity.UserRole
import java.util.UUID

/**
 * Unit test for JwtTokenProvider.
 *
 * Verifies that the username claim is embedded into access tokens and
 * correctly round-tripped through parseAndValidate.
 */
class JwtTokenProviderTest {

    // Deterministic 32-byte test secret — mirrors what integration tests use.
    private val provider = JwtTokenProvider(
        secret = "test-jwt-secret-32-bytes-padded!!",
        accessTtlMinutes = 60L,
        refreshTtlDays = 7L,
    )

    @Test
    fun `generateAccess includes username claim that survives round-trip`() {
        val userId = UUID.randomUUID()
        val email = "roundtrip@diplom.local"
        val role = UserRole.STUDENT
        val username = "roundtrip_user"

        val token = provider.generateAccess(userId, email, role, username)
        val claims = provider.parseAndValidate(token)

        assertEquals(userId.toString(), claims.subject)
        assertEquals("access", claims.typ)
        assertEquals(email, claims.email)
        assertEquals(role, claims.role)
        assertEquals(username, claims.username)
    }

    @Test
    fun `generateRefresh does not include username claim`() {
        val userId = UUID.randomUUID()

        val token = provider.generateRefresh(userId)
        val claims = provider.parseAndValidate(token)

        assertEquals(userId.toString(), claims.subject)
        assertEquals("refresh", claims.typ)
        // Refresh tokens carry no username or email.
        assertEquals(null, claims.username)
        assertEquals(null, claims.email)
    }

    @Test
    fun `generateAccess with different usernames produces distinct tokens`() {
        val userId = UUID.randomUUID()
        val email = "distinct@diplom.local"
        val role = UserRole.TEACHER

        val token1 = provider.generateAccess(userId, email, role, "alice_1")
        val token2 = provider.generateAccess(userId, email, role, "bob_2")

        assert(token1 != token2) { "Tokens with different usernames must be distinct" }

        val claims1 = provider.parseAndValidate(token1)
        val claims2 = provider.parseAndValidate(token2)

        assertEquals("alice_1", claims1.username)
        assertEquals("bob_2", claims2.username)
    }

    @Test
    fun `parseAndValidate extracts all standard claims correctly`() {
        val userId = UUID.randomUUID()
        val token = provider.generateAccess(userId, "full@diplom.local", UserRole.TEACHER, "full_user")
        val claims = provider.parseAndValidate(token)

        assertNotNull(claims.subject)
        assertNotNull(claims.email)
        assertNotNull(claims.role)
        assertNotNull(claims.username)
        assertEquals(UserRole.TEACHER, claims.role)
        assertEquals("full_user", claims.username)
    }
}
