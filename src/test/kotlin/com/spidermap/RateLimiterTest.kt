package com.spidermap

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    private val window = Duration.ofMinutes(5)
    private val maxAttempts = 5

    private fun limiter(clock: TestClock = TestClock()) = RateLimiter(maxAttempts, window, clock)

    @Test
    fun `attempts up to the limit are allowed`() {
        val limits = limiter()

        repeat(maxAttempts) { attempt ->
            assertTrue(limits.allow("1.2.3.4"), "attempt ${attempt + 1} was blocked")
        }
    }

    @Test
    fun `the attempt after the limit is blocked`() {
        val limits = limiter()
        repeat(maxAttempts) { limits.allow("1.2.3.4") }

        assertFalse(limits.allow("1.2.3.4"))
    }

    @Test
    fun `blocked attempts still count`() {
        // Otherwise a caller who is already limited could keep trying for free,
        // and the limit would only slow down the polite.
        val clock = TestClock()
        val limits = limiter(clock)
        repeat(maxAttempts + 10) { limits.allow("1.2.3.4") }

        // Past the limit but inside the window — those extra attempts must not
        // have been forgotten.
        clock.advance(window.minusSeconds(1))
        assertFalse(limits.allow("1.2.3.4"), "attempts stopped being counted once blocked")
    }

    @Test
    fun `callers are limited independently`() {
        val limits = limiter()
        repeat(maxAttempts + 1) { limits.allow("1.2.3.4") }

        assertTrue(limits.allow("5.6.7.8"), "one caller's attempts limited another")
    }

    @Test
    fun `the window slides rather than resetting in blocks`() {
        // With fixed buckets an attacker gets the full allowance twice by
        // straddling a boundary. Here, letting the oldest attempt age out buys
        // exactly one more, and no more.
        val clock = TestClock()
        val limits = limiter(clock)

        repeat(maxAttempts) { limits.allow("1.2.3.4") }
        assertFalse(limits.allow("1.2.3.4"))

        // Just past the age of the first attempt only.
        clock.advance(window.plusSeconds(1))
        assertTrue(limits.allow("1.2.3.4"), "the window never released")
    }

    @Test
    fun `attempts are forgotten once the window passes`() {
        val clock = TestClock()
        val limits = limiter(clock)
        repeat(maxAttempts) { limits.allow("1.2.3.4") }

        clock.advance(window.plusSeconds(1))

        repeat(maxAttempts) { attempt ->
            assertTrue(limits.allow("1.2.3.4"), "attempt ${attempt + 1} blocked after the window")
        }
    }

    @Test
    fun `remaining counts down and floors at zero`() {
        val limits = limiter()
        assertEquals(maxAttempts, limits.remaining("1.2.3.4"))

        limits.allow("1.2.3.4")
        assertEquals(maxAttempts - 1, limits.remaining("1.2.3.4"))

        repeat(maxAttempts + 5) { limits.allow("1.2.3.4") }
        assertEquals(0, limits.remaining("1.2.3.4"), "remaining went negative")
    }

    @Test
    fun `a successful exchange clears the caller`() {
        // A legitimate editor who mistyped twice should not spend the rest of
        // the window one typo from being locked out.
        val limits = limiter()
        repeat(maxAttempts - 1) { limits.allow("1.2.3.4") }

        limits.reset("1.2.3.4")

        assertEquals(maxAttempts, limits.remaining("1.2.3.4"))
        repeat(maxAttempts) { assertTrue(limits.allow("1.2.3.4")) }
    }

    @Test
    fun `an unseen caller has a full allowance`() {
        assertEquals(maxAttempts, limiter().remaining("never.seen.before"))
    }
}
