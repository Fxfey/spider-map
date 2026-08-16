package com.spidermap

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived, single-use codes linking an in-game player to a browser session.
 *
 * The point of the whole flow (SDLC §2) is that the browser never asserts an
 * identity. `/claims weblogin` runs server-side where the player's UUID is
 * already Mojang-verified; the browser only ever sends back a code, and the
 * server resolves it to the UUID itself.
 *
 * In memory only, deliberately: a code is valid for minutes, so surviving a
 * restart would buy nothing and would mean writing credentials to disk.
 *
 * [clock] is injectable so expiry can be tested without sleeping.
 */
class LoginCodeStore(
    private val codeLifetime: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {

    private data class Issued(val playerId: UUID, val expiresAt: Instant)

    private val codes = ConcurrentHashMap<String, Issued>()

    /**
     * Issues a fresh code for [playerId], invalidating any it already holds.
     *
     * Superseding rather than accumulating: a player running the command twice
     * has decided the first code went astray, and leaving it live would mean
     * more valid credentials in the world than the player believes exist.
     */
    fun issue(playerId: UUID): String {
        purgeExpired()
        codes.entries.removeIf { it.value.playerId == playerId }

        val code = generateUnusedCode()
        codes[code] = Issued(playerId, clock.instant().plus(codeLifetime))
        return code
    }

    /**
     * Redeems a code, returning the player it belongs to.
     *
     * Single-use: a successful lookup removes it, so a code sniffed in transit
     * is worthless the moment it has been spent. Returns null for unknown,
     * already-used and expired codes alike — the caller must not tell them
     * apart, or the difference becomes a way to probe which codes exist.
     */
    fun consume(code: String): UUID? {
        purgeExpired()

        val issued = codes.remove(code.trim()) ?: return null

        // Removed above, so an expired code is spent by the attempt either way.
        if (issued.expiresAt.isBefore(clock.instant())) return null

        return issued.playerId
    }

    /** Live codes, for tests and diagnostics. */
    fun activeCount(): Int {
        purgeExpired()
        return codes.size
    }

    private fun purgeExpired() {
        val now = clock.instant()
        codes.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }

    private fun generateUnusedCode(): String {
        // Bounded rather than a while(true): if this many draws all collide,
        // something is badly wrong and hanging the calling thread is worse than
        // an exception.
        repeat(MAX_GENERATION_ATTEMPTS) {
            val code = (1..CODE_LENGTH)
                .map { random.nextInt(10) }
                .joinToString("")

            if (!codes.containsKey(code)) return code
        }

        error("Could not generate an unused login code after $MAX_GENERATION_ATTEMPTS attempts")
    }

    private companion object {
        /** Six digits, per SDLC §2 — short enough to read off chat and retype. */
        const val CODE_LENGTH = 6

        const val MAX_GENERATION_ATTEMPTS = 100

        /**
         * SecureRandom, not Random. These are credentials: a predictable
         * sequence would let someone else's code be guessed from their own.
         */
        val random = SecureRandom()
    }
}
