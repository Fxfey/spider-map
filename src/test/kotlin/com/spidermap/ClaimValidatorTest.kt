package com.spidermap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimValidatorTest {

    private val validator = ClaimValidator(vertexCap = DEFAULT_CAP)

    /** Points on a circle, so no three are collinear and none repeat. */
    private fun polygon(pointCount: Int): List<Vertex> =
        (0 until pointCount).map { i ->
            val angle = 2 * Math.PI * i / pointCount
            Vertex(
                x = (Math.cos(angle) * 1000).toInt(),
                z = (Math.sin(angle) * 1000).toInt(),
            )
        }

    @Test
    fun `rejects an empty vertex list`() {
        assertNotNull(validator.validate(emptyList()))
    }

    @Test
    fun `rejects a single point`() {
        assertNotNull(validator.validate(polygon(1)))
    }

    @Test
    fun `rejects two points, which describe a line and enclose nothing`() {
        val error = validator.validate(polygon(2))
        assertNotNull(error)
        assertTrue("at least 3" in error, "unhelpful message: $error")
    }

    @Test
    fun `accepts exactly three points, the smallest real polygon`() {
        assertNull(validator.validate(polygon(3)))
    }

    @Test
    fun `accepts a typical four point claim`() {
        assertNull(validator.validate(polygon(4)))
    }

    @Test
    fun `accepts exactly the cap`() {
        assertNull(validator.validate(polygon(DEFAULT_CAP)))
    }

    @Test
    fun `rejects one point over the cap`() {
        val error = validator.validate(polygon(DEFAULT_CAP + 1))
        assertNotNull(error)
        assertTrue("at most $DEFAULT_CAP" in error, "unhelpful message: $error")
    }

    @Test
    fun `the two failures give distinct messages`() {
        // 2.1's "done when" is that each boundary is distinguishable, so a user
        // can tell whether to add points or remove them.
        val tooFew = validator.validate(polygon(2))
        val tooMany = validator.validate(polygon(DEFAULT_CAP + 1))

        assertNotNull(tooFew)
        assertNotNull(tooMany)
        assertTrue(tooFew != tooMany, "both boundaries produced the same message")
    }

    @Test
    fun `honours a cap other than the default`() {
        // Proves the value is threaded from config rather than hardcoded.
        val strict = ClaimValidator(vertexCap = 4)

        assertNull(strict.validate(polygon(4)))
        assertNotNull(strict.validate(polygon(5)))

        // The message must quote the configured cap, not the default.
        assertTrue("at most 4" in strict.validate(polygon(5))!!)
    }

    @Test
    fun `the reported count is the actual count`() {
        val error = validator.validate(polygon(2))
        assertNotNull(error)
        assertTrue("has 2" in error, "message should say how many were given: $error")
    }

    private companion object {
        /** Matches the config.yml default, so the tests mirror a real server. */
        const val DEFAULT_CAP = 50
    }
}
