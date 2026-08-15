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

    // ---- 2.2: self-intersection --------------------------------------------

    @Test
    fun `rejects the classic bowtie`() {
        // Corners visited in an order that makes the two edges cross:
        // bottom-left, top-right, bottom-right, top-left.
        val bowtie = listOf(
            Vertex(0, 0),
            Vertex(64, 64),
            Vertex(64, 0),
            Vertex(0, 64),
        )

        val error = validator.validate(bowtie)
        assertNotNull(error)
        assertTrue("crosses itself" in error, "unhelpful message: $error")
    }

    @Test
    fun `accepts a plain square`() {
        assertNull(
            validator.validate(
                listOf(Vertex(0, 0), Vertex(64, 0), Vertex(64, 64), Vertex(0, 64)),
            )
        )
    }

    @Test
    fun `accepts a concave L shape`() {
        // The check must not be "is this convex" — most real claims hug terrain
        // and are concave.
        val lShape = listOf(
            Vertex(0, 0),
            Vertex(64, 0),
            Vertex(64, 32),
            Vertex(32, 32),
            Vertex(32, 64),
            Vertex(0, 64),
        )

        assertNull(validator.validate(lShape), "a concave shape was wrongly rejected")
    }

    @Test
    fun `accepts a deeply concave comb`() {
        // Several inward spikes: plenty of reflex corners, still no crossing.
        val comb = listOf(
            Vertex(0, 0),
            Vertex(96, 0),
            Vertex(96, 64),
            Vertex(80, 64),
            Vertex(80, 16),
            Vertex(64, 16),
            Vertex(64, 64),
            Vertex(48, 64),
            Vertex(48, 16),
            Vertex(32, 16),
            Vertex(32, 64),
            Vertex(0, 64),
        )

        assertNull(validator.validate(comb), "a concave comb was wrongly rejected")
    }

    @Test
    fun `rejects a shape whose edge clips a far corner`() {
        // No clean X crossing — one edge merely passes through another's
        // endpoint. Still leaves the polygon without a coherent inside.
        val touching = listOf(
            Vertex(0, 0),
            Vertex(64, 0),
            Vertex(32, 32),
            Vertex(64, 64),
            Vertex(0, 64),
            Vertex(32, 32),
        )

        assertNotNull(validator.validate(touching))
    }

    @Test
    fun `accepts a triangle`() {
        assertNull(validator.validate(listOf(Vertex(0, 0), Vertex(64, 0), Vertex(32, 64))))
    }

    @Test
    fun `accepts a chunk aligned claim at real world coordinates`() {
        // The schema example from SDLC §4, at coordinates a server would use.
        assertNull(
            validator.validate(
                listOf(Vertex(128, -64), Vertex(144, -64), Vertex(144, -48), Vertex(128, -48)),
            )
        )
    }

    @Test
    fun `detects crossings far from the origin without integer overflow`() {
        // Near the world border. With Int arithmetic the cross product
        // overflows, flips sign, and reports the wrong answer.
        val far = 30_000_000
        val bowtie = listOf(
            Vertex(-far, -far),
            Vertex(far, far),
            Vertex(far, -far),
            Vertex(-far, far),
        )

        assertNotNull(validator.validate(bowtie), "overflow hid a real crossing")
    }

    @Test
    fun `a huge valid claim near the world border is still accepted`() {
        val far = 30_000_000
        val square = listOf(
            Vertex(-far, -far),
            Vertex(far, -far),
            Vertex(far, far),
            Vertex(-far, far),
        )

        assertNull(validator.validate(square), "overflow invented a crossing")
    }

    @Test
    fun `vertex count is checked before self-intersection`() {
        // Two points cannot self-intersect; the count message is the useful one.
        val error = validator.validate(listOf(Vertex(0, 0), Vertex(64, 64)))
        assertNotNull(error)
        assertTrue("at least 3" in error, "wrong rule reported first: $error")
    }

    private companion object {
        /** Matches the config.yml default, so the tests mirror a real server. */
        const val DEFAULT_CAP = 50
    }
}
