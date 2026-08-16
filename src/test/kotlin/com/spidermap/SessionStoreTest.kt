package com.spidermap

import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

    private val lifetime = Duration.ofHours(12)
    private val player = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val other = UUID.fromString("99999999-8888-7777-6666-555555555555")

    private fun store(clock: TestClock = TestClock()) = SessionStore(lifetime, clock)

    @Test
    fun `a token resolves to the player it was issued for`() {
        val sessions = store()
        assertEquals(player, sessions.resolve(sessions.create(player)))
    }

    @Test
    fun `an unknown token resolves to nothing`() {
        assertNull(store().resolve("not-a-real-token"))
    }

    @Test
    fun `null and blank tokens are rejected without fuss`() {
        // The header is simply absent on an unauthenticated request, which is
        // normal traffic rather than an error.
        val sessions = store()
        assertNull(sessions.resolve(null))
        assertNull(sessions.resolve(""))
        assertNull(sessions.resolve("   "))
    }

    @Test
    fun `tokens are unguessably long and url safe`() {
        val token = store().create(player)

        // 32 bytes, base64url without padding.
        assertTrue(token.length >= 40, "token looks too short: ${token.length} chars")
        assertTrue(
            token.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "token needs escaping in a header or URL: '$token'",
        )
    }

    @Test
    fun `every token is different`() {
        val sessions = store()
        val issued = (1..200).map { sessions.create(player) }

        assertEquals(issued.size, issued.toSet().size, "a token was issued twice")
    }

    @Test
    fun `a token reveals nothing about the player`() {
        // Tokens are opaque: an attacker holding one cannot craft another for a
        // chosen victim.
        val sessions = store()
        val token = sessions.create(player)

        assertTrue(player.toString() !in token)
        assertTrue(player.toString().replace("-", "") !in token)
    }

    @Test
    fun `a session expires after the configured lifetime`() {
        val clock = TestClock()
        val sessions = store(clock)
        val token = sessions.create(player)

        clock.advance(lifetime.plusSeconds(1))

        assertNull(sessions.resolve(token), "an expired session was accepted")
    }

    @Test
    fun `a session still works just before it expires`() {
        val clock = TestClock()
        val sessions = store(clock)
        val token = sessions.create(player)

        clock.advance(lifetime.minusSeconds(1))

        assertEquals(player, sessions.resolve(token))
    }

    @Test
    fun `a session survives repeated use, unlike a login code`() {
        // Codes are single-use; sessions are not, or every request would log
        // the editor out.
        val sessions = store()
        val token = sessions.create(player)

        repeat(5) { assertEquals(player, sessions.resolve(token)) }
    }

    @Test
    fun `revoking ends one session and leaves others alone`() {
        val sessions = store()
        val laptop = sessions.create(player)
        val phone = sessions.create(player)

        sessions.revoke(laptop)

        assertNull(sessions.resolve(laptop))
        assertEquals(player, sessions.resolve(phone), "logging out one browser ended another")
    }

    @Test
    fun `revoking all for a player leaves other players alone`() {
        // What removing someone's editor rights should do.
        val sessions = store()
        val theirs = sessions.create(player)
        val mine = sessions.create(other)

        sessions.revokeAllFor(player)

        assertNull(sessions.resolve(theirs))
        assertEquals(other, sessions.resolve(mine))
    }

    @Test
    fun `expired sessions stop taking up space`() {
        val clock = TestClock()
        val sessions = store(clock)
        sessions.create(player)
        sessions.create(other)
        assertEquals(2, sessions.activeCount())

        clock.advance(lifetime.plusSeconds(1))

        assertEquals(0, sessions.activeCount(), "expired sessions were retained")
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val sessions = store()
        val token = sessions.create(player)

        assertEquals(player, sessions.resolve("  $token  "))
    }
}
