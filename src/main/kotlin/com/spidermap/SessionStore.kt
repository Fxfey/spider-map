package com.spidermap

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Browser sessions: an opaque token standing in for a Mojang-verified UUID.
 *
 * The client never supplies an identity (SDLC §2). It sends a token; the server
 * looks up which player that token was issued to. Nothing about the UUID is
 * derivable from the token itself, so a token cannot be forged for a chosen
 * player — only guessed, against 256 bits.
 *
 * In memory, so a restart logs editors out. That is the honest trade for not
 * writing live credentials to disk, and re-running `/claims weblogin` is cheap.
 *
 * [clock] is injectable so expiry can be tested without sleeping.
 */
class SessionStore(
    private val sessionLifetime: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {

    private data class Session(val playerId: UUID, val expiresAt: Instant)

    private val sessions = ConcurrentHashMap<String, Session>()

    /** Issues a token for [playerId]. Existing sessions are left alone. */
    fun create(playerId: UUID): String {
        purgeExpired()

        val token = newToken()
        sessions[token] = Session(playerId, clock.instant().plus(sessionLifetime))
        return token
    }

    /**
     * The player this token belongs to, or null if it is unknown or expired.
     *
     * Unknown and expired are deliberately indistinguishable — telling them
     * apart would confirm that a guessed token once existed.
     */
    fun resolve(token: String?): UUID? {
        if (token.isNullOrBlank()) return null
        purgeExpired()

        val session = sessions[token.trim()] ?: return null
        if (session.expiresAt.isBefore(clock.instant())) {
            sessions.remove(token.trim())
            return null
        }

        return session.playerId
    }

    /** Ends one session — a logout, leaving that editor's other browsers alone. */
    fun revoke(token: String?) {
        if (token.isNullOrBlank()) return
        sessions.remove(token.trim())
    }

    /** Ends every session for a player, e.g. when their editor rights are removed. */
    fun revokeAllFor(playerId: UUID) {
        sessions.entries.removeIf { it.value.playerId == playerId }
    }

    /** Live sessions, for tests and diagnostics. */
    fun activeCount(): Int {
        purgeExpired()
        return sessions.size
    }

    private fun purgeExpired() {
        val now = clock.instant()
        sessions.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }

    /**
     * 256 bits from SecureRandom, base64url so it survives being put in a
     * header or URL without escaping.
     */
    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES = 32

        val random = SecureRandom()
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
