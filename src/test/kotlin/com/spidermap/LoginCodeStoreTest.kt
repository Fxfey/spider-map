package com.spidermap

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock the test moves by hand, so expiry needs no sleeping. */
private class TestClock(private var now: Instant = Instant.parse("2026-08-15T12:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class LoginCodeStoreTest {

    private val lifetime = Duration.ofMinutes(3)
    private val player = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val other = UUID.fromString("99999999-8888-7777-6666-555555555555")

    private fun store(clock: TestClock = TestClock()) = LoginCodeStore(lifetime, clock)

    @Test
    fun `a code resolves to the player it was issued for`() {
        val codes = store()
        val code = codes.issue(player)

        assertEquals(player, codes.consume(code))
    }

    @Test
    fun `codes are six digits`() {
        val code = store().issue(player)

        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() }, "expected digits only, got '$code'")
    }

    @Test
    fun `two calls produce different codes`() {
        // 5.2's stated verify.
        val codes = store()
        assertNotEquals(codes.issue(player), codes.issue(player))
    }

    @Test
    fun `a code is single use`() {
        val codes = store()
        val code = codes.issue(player)

        assertEquals(player, codes.consume(code))
        // Sniffed in transit, it is worthless once spent.
        assertNull(codes.consume(code), "a code was accepted twice")
    }

    @Test
    fun `an unknown code is rejected`() {
        assertNull(store().consume("000000"))
    }

    @Test
    fun `issuing again invalidates the previous code`() {
        // A player running the command twice has decided the first went astray;
        // leaving it live would mean more valid credentials than they believe
        // exist.
        val codes = store()
        val first = codes.issue(player)
        val second = codes.issue(player)

        assertNull(codes.consume(first), "the superseded code still worked")
        assertEquals(player, codes.consume(second))
    }

    @Test
    fun `a code expires after the configured window`() {
        val clock = TestClock()
        val codes = store(clock)
        val code = codes.issue(player)

        clock.advance(lifetime.plusSeconds(1))

        assertNull(codes.consume(code), "an expired code was accepted")
    }

    @Test
    fun `a code still works just before it expires`() {
        val clock = TestClock()
        val codes = store(clock)
        val code = codes.issue(player)

        clock.advance(lifetime.minusSeconds(1))

        assertEquals(player, codes.consume(code))
    }

    @Test
    fun `expired codes stop taking up space`() {
        val clock = TestClock()
        val codes = store(clock)
        codes.issue(player)
        codes.issue(other)
        assertEquals(2, codes.activeCount())

        clock.advance(lifetime.plusSeconds(1))

        assertEquals(0, codes.activeCount(), "expired codes were retained")
    }

    @Test
    fun `codes for different players are independent`() {
        val codes = store()
        val theirs = codes.issue(player)
        val mine = codes.issue(other)

        assertEquals(player, codes.consume(theirs))
        assertEquals(other, codes.consume(mine))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        // Typed into a browser field, so a stray space is likely and is not the
        // user getting it wrong.
        val codes = store()
        val code = codes.issue(player)

        assertEquals(player, codes.consume("  $code "))
    }

    @Test
    fun `codes are not sequential`() {
        // A predictable sequence would let one player guess another's code.
        val codes = store()
        val issued = (1..50).map { codes.issue(UUID.randomUUID()) }

        assertEquals(issued.size, issued.toSet().size, "codes repeated within one run")

        val consecutive = issued.zipWithNext().count { (a, b) -> b.toInt() == a.toInt() + 1 }
        assertTrue(consecutive < 3, "codes look sequential: $consecutive consecutive pairs")
    }
}
