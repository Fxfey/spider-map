package com.spidermap

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A sliding-window attempt limiter, keyed by caller (in practice, IP).
 *
 * SDLC §2 asks for this on code entry specifically: a six-digit code is only a
 * million possibilities, which is nothing to a script but a great deal to a
 * person typing. The limit exists to make the difference between those two
 * matter — it is not meant to inconvenience someone who fat-fingered a digit.
 *
 * Sliding rather than fixed buckets: with fixed windows an attacker gets the
 * full allowance twice in quick succession by straddling a boundary.
 *
 * [clock] is injectable so the window can be tested without sleeping.
 */
class RateLimiter(
    private val maxAttempts: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()

    /**
     * Records an attempt and reports whether it is allowed.
     *
     * Counting every attempt, including rejected ones, is the point: otherwise
     * a caller who is already being limited could keep trying for free.
     */
    @Synchronized
    fun allow(key: String): Boolean {
        val now = clock.instant()
        val cutoff = now.minus(window)

        val recent = attempts.getOrPut(key) { mutableListOf() }
        recent.removeIf { it.isBefore(cutoff) }
        recent.add(now)

        // Stops the map growing without bound from one-off callers.
        if (attempts.size > MAX_TRACKED_KEYS) forgetIdle(cutoff)

        return recent.size <= maxAttempts
    }

    /** How many attempts remain for [key] in the current window. */
    @Synchronized
    fun remaining(key: String): Int {
        val cutoff = clock.instant().minus(window)
        val recent = attempts[key] ?: return maxAttempts

        recent.removeIf { it.isBefore(cutoff) }
        return (maxAttempts - recent.size).coerceAtLeast(0)
    }

    /** Forgets a caller entirely — used after a success, so a legitimate
     *  editor is not left near the limit by earlier typos. */
    @Synchronized
    fun reset(key: String) {
        attempts.remove(key)
    }

    private fun forgetIdle(cutoff: Instant) {
        attempts.entries.removeIf { (_, times) ->
            times.removeIf { it.isBefore(cutoff) }
            times.isEmpty()
        }
    }

    private companion object {
        /**
         * Only a guard against unbounded growth under a spray of unique IPs;
         * entries are pruned by age well before this normally matters.
         */
        const val MAX_TRACKED_KEYS = 10_000
    }
}
