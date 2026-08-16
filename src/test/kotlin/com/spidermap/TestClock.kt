package com.spidermap

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A clock the test moves by hand.
 *
 * Everything with an expiry — login codes, sessions, rate-limit windows — is
 * tested by advancing this rather than sleeping, so the suite stays instant and
 * cannot go flaky on a slow machine.
 */
class TestClock(private var now: Instant = Instant.parse("2026-08-15T12:00:00Z")) : Clock() {

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(duration: java.time.Duration) {
        now = now.plus(duration)
    }
}
