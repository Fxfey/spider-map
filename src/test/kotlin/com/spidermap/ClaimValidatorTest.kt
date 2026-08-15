package com.spidermap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimValidatorTest {

    private val validator = ClaimValidator(vertexCap = DEFAULT_CAP)

    /**
     * Most tests care about one world, so it defaults here rather than being
     * repeated at every call site. The world-scoping tests pass it explicitly.
     */
    private fun validate(
        vertices: List<Vertex>,
        existing: List<Claim> = emptyList(),
        excludeClaimId: String? = null,
        world: String = "world",
    ): String? = validator.validate(vertices, world, existing, excludeClaimId)

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
        assertNotNull(validate(emptyList()))
    }

    @Test
    fun `rejects a single point`() {
        assertNotNull(validate(polygon(1)))
    }

    @Test
    fun `rejects two points, which describe a line and enclose nothing`() {
        val error = validate(polygon(2))
        assertNotNull(error)
        assertTrue("at least 3" in error, "unhelpful message: $error")
    }

    @Test
    fun `accepts exactly three points, the smallest real polygon`() {
        assertNull(validate(polygon(3)))
    }

    @Test
    fun `accepts a typical four point claim`() {
        assertNull(validate(polygon(4)))
    }

    @Test
    fun `accepts exactly the cap`() {
        assertNull(validate(polygon(DEFAULT_CAP)))
    }

    @Test
    fun `rejects one point over the cap`() {
        val error = validate(polygon(DEFAULT_CAP + 1))
        assertNotNull(error)
        assertTrue("at most $DEFAULT_CAP" in error, "unhelpful message: $error")
    }

    @Test
    fun `the two failures give distinct messages`() {
        // 2.1's "done when" is that each boundary is distinguishable, so a user
        // can tell whether to add points or remove them.
        val tooFew = validate(polygon(2))
        val tooMany = validate(polygon(DEFAULT_CAP + 1))

        assertNotNull(tooFew)
        assertNotNull(tooMany)
        assertTrue(tooFew != tooMany, "both boundaries produced the same message")
    }

    @Test
    fun `honours a cap other than the default`() {
        // Proves the value is threaded from config rather than hardcoded.
        val strict = ClaimValidator(vertexCap = 4)

        assertNull(strict.validate(polygon(4), "world"))
        assertNotNull(strict.validate(polygon(5), "world"))

        // The message must quote the configured cap, not the default.
        assertTrue("at most 4" in strict.validate(polygon(5), "world")!!)
    }

    @Test
    fun `the reported count is the actual count`() {
        val error = validate(polygon(2))
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

        val error = validate(bowtie)
        assertNotNull(error)
        assertTrue("crosses itself" in error, "unhelpful message: $error")
    }

    @Test
    fun `accepts a plain square`() {
        assertNull(
            validate(
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

        assertNull(validate(lShape), "a concave shape was wrongly rejected")
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

        assertNull(validate(comb), "a concave comb was wrongly rejected")
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

        assertNotNull(validate(touching))
    }

    @Test
    fun `accepts a triangle`() {
        assertNull(validate(listOf(Vertex(0, 0), Vertex(64, 0), Vertex(32, 64))))
    }

    @Test
    fun `accepts a chunk aligned claim at real world coordinates`() {
        // The schema example from SDLC §4, at coordinates a server would use.
        assertNull(
            validate(
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

        assertNotNull(validate(bowtie), "overflow hid a real crossing")
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

        assertNull(validate(square), "overflow invented a crossing")
    }

    @Test
    fun `vertex count is checked before self-intersection`() {
        // Two points cannot self-intersect; the count message is the useful one.
        val error = validate(listOf(Vertex(0, 0), Vertex(64, 64)))
        assertNotNull(error)
        assertTrue("at least 3" in error, "wrong rule reported first: $error")
    }

    // ---- 2.3: minimum area --------------------------------------------------

    @Test
    fun `rejects three exactly collinear points`() {
        // No crossing, no touching — 2.2 lets this straight through. It encloses
        // nothing at all.
        val flat = listOf(Vertex(0, 0), Vertex(64, 0), Vertex(128, 0))

        val error = validate(flat)
        assertNotNull(error)
        assertTrue("too small or too thin" in error, "unhelpful message: $error")
    }

    @Test
    fun `rejects three nearly collinear points`() {
        // The checklist's case: a sliver one block deep over a 128 block span.
        val sliver = listOf(Vertex(0, 0), Vertex(128, 0), Vertex(64, 1))

        assertNotNull(validate(sliver))
    }

    @Test
    fun `rejects a repeated corner that collapses the shape`() {
        val collapsed = listOf(Vertex(0, 0), Vertex(0, 0), Vertex(64, 0))

        assertNotNull(validate(collapsed))
    }

    @Test
    fun `accepts the smallest drawable shape, a 16 by 16 right triangle`() {
        // Area exactly 128 — the floor. This is drawable in the UI, so the
        // server must not refuse it.
        val minimal = listOf(Vertex(0, 0), Vertex(16, 0), Vertex(0, 16))

        assertNull(validate(minimal), "the smallest drawable shape was rejected")
    }

    @Test
    fun `accepts a single chunk square`() {
        val chunk = listOf(Vertex(0, 0), Vertex(16, 0), Vertex(16, 16), Vertex(0, 16))

        assertNull(validate(chunk))
    }

    @Test
    fun `winding order does not affect the area check`() {
        val clockwise = listOf(Vertex(0, 0), Vertex(16, 0), Vertex(16, 16), Vertex(0, 16))
        val anticlockwise = clockwise.reversed()

        assertNull(validate(clockwise))
        assertNull(validate(anticlockwise), "reversing the points changed the verdict")
    }

    @Test
    fun `area is computed correctly for a concave shape`() {
        // Shoelace handles concave polygons; a bounding-box approximation would
        // not, and would wrongly accept a thin concave sliver.
        val lShape = listOf(
            Vertex(0, 0),
            Vertex(64, 0),
            Vertex(64, 32),
            Vertex(32, 32),
            Vertex(32, 64),
            Vertex(0, 64),
        )

        assertNull(validate(lShape))
    }

    @Test
    fun `a huge claim near the world border does not overflow the area sum`() {
        val far = 30_000_000
        val square = listOf(
            Vertex(-far, -far),
            Vertex(far, -far),
            Vertex(far, far),
            Vertex(-far, far),
        )

        assertNull(validate(square), "the area sum overflowed and rejected a valid claim")
    }

    @Test
    fun `self-intersection is checked before area`() {
        // A bowtie's shoelace area can come out large, so ordering matters: the
        // crossing is the real problem and should be what gets reported.
        val bowtie = listOf(Vertex(0, 0), Vertex(64, 64), Vertex(64, 0), Vertex(0, 64))

        val error = validate(bowtie)
        assertNotNull(error)
        assertTrue("crosses itself" in error, "wrong rule reported first: $error")
    }

    // ---- 2.4: overlap vs hug ------------------------------------------------

    /** A claim occupying the axis-aligned box from (x1,z1) to (x2,z2). */
    private fun boxClaim(
        id: String,
        title: String,
        x1: Int,
        z1: Int,
        x2: Int,
        z2: Int,
        world: String = "world",
    ) = Claim(
        id = id,
        title = title,
        world = world,
        createdByUuid = "eee00000-1111-2222-3333-444444444444",
        vertices = listOf(Vertex(x1, z1), Vertex(x2, z1), Vertex(x2, z2), Vertex(x1, z2)),
        createdAt = "2026-08-15T12:00:00Z",
        updatedAt = "2026-08-15T12:00:00Z",
    )

    private fun box(x1: Int, z1: Int, x2: Int, z2: Int) =
        listOf(Vertex(x1, z1), Vertex(x2, z1), Vertex(x2, z2), Vertex(x1, z2))

    @Test
    fun `(a) two claims sharing an edge are both accepted`() {
        // The headline case. Neighbours hugging along x = 64 is expected and
        // must not be mistaken for an overlap.
        val existing = boxClaim("first", "Willowbrook Manor", 0, 0, 64, 64)
        val abutting = box(64, 0, 128, 64)

        assertNull(
            validate(abutting, listOf(existing)),
            "claims sharing a border were wrongly rejected as overlapping",
        )
    }

    @Test
    fun `(b) two claims with real interior overlap are rejected`() {
        val existing = boxClaim("first", "Willowbrook Manor", 0, 0, 64, 64)
        val overlapping = box(32, 32, 96, 96)

        val error = validate(overlapping, listOf(existing))
        assertNotNull(error)
        assertTrue("overlaps" in error, "unhelpful message: $error")
        assertTrue("Willowbrook Manor" in error, "the message should name the claim in the way: $error")
    }

    @Test
    fun `(c) editing a claim is not rejected for overlapping itself`() {
        val existing = boxClaim("being-edited", "Willowbrook Manor", 0, 0, 64, 64)

        // Same footprint, nudged — overlaps its own stored shape heavily.
        val edited = box(0, 0, 80, 64)

        assertNull(
            validate(edited, listOf(existing), excludeClaimId = "being-edited"),
            "a claim was rejected for overlapping its own previous outline",
        )
    }

    @Test
    fun `an edit still collides with other claims`() {
        // The exclusion must be narrow: skipping *itself* cannot mean skipping
        // the check entirely.
        val self = boxClaim("being-edited", "Mine", 0, 0, 64, 64)
        val neighbour = boxClaim("other", "Theirs", 128, 0, 192, 64)

        val grownIntoNeighbour = box(0, 0, 160, 64)

        val error = validate(
            grownIntoNeighbour,
            listOf(self, neighbour),
            excludeClaimId = "being-edited",
        )
        assertNotNull(error)
        assertTrue("Theirs" in error, "wrong claim named: $error")
    }

    @Test
    fun `claims touching at a single corner are accepted`() {
        // Diagonal neighbours meeting at exactly one point: boundaries touch,
        // interiors do not.
        val existing = boxClaim("first", "Corner Neighbour", 0, 0, 64, 64)
        val diagonal = box(64, 64, 128, 128)

        assertNull(validate(diagonal, listOf(existing)))
    }

    @Test
    fun `a claim entirely inside another is rejected`() {
        // Nested claims are out of scope for v1 (SDLC §2), and no edge is
        // crossed here — only containment.
        val existing = boxClaim("first", "Big Estate", 0, 0, 256, 256)
        val inside = box(64, 64, 128, 128)

        assertNotNull(validate(inside, listOf(existing)))
    }

    @Test
    fun `a claim entirely swallowing another is rejected`() {
        val existing = boxClaim("first", "Small Plot", 64, 64, 128, 128)
        val swallowing = box(0, 0, 256, 256)

        assertNotNull(validate(swallowing, listOf(existing)))
    }

    @Test
    fun `overlapping by a single block is still an overlap`() {
        // One block of shared ground, not a shared border.
        val existing = boxClaim("first", "Willowbrook Manor", 0, 0, 64, 64)
        val barelyOver = box(63, 0, 128, 64)

        assertNotNull(validate(barelyOver, listOf(existing)))
    }

    @Test
    fun `a claim in empty space is accepted`() {
        val existing = boxClaim("first", "Far Away", 0, 0, 64, 64)
        val elsewhere = box(1000, 1000, 1064, 1064)

        assertNull(validate(elsewhere, listOf(existing)))
    }

    @Test
    fun `a concave claim nestling into another's notch is accepted`() {
        // The hardest legitimate case: an L-shape and the square that fills its
        // notch share two edges but no ground.
        val lShape = Claim(
            id = "l-shape",
            title = "L Shape",
            world = "world",
            createdByUuid = "eee00000-1111-2222-3333-444444444444",
            vertices = listOf(
                Vertex(0, 0),
                Vertex(64, 0),
                Vertex(64, 32),
                Vertex(32, 32),
                Vertex(32, 64),
                Vertex(0, 64),
            ),
            createdAt = "2026-08-15T12:00:00Z",
            updatedAt = "2026-08-15T12:00:00Z",
        )
        val fillsTheNotch = box(32, 32, 64, 64)

        assertNull(
            validate(fillsTheNotch, listOf(lShape)),
            "a claim filling a neighbour's notch was wrongly rejected",
        )
    }

    // ---- containment: no claim inside another (SDLC §2, nested claims are out) ----

    @Test
    fun `an identical claim is rejected`() {
        val existing = boxClaim("first", "Willowbrook Manor", 0, 0, 64, 64)

        assertNotNull(
            validate(box(0, 0, 64, 64), listOf(existing)),
            "an exact duplicate was accepted",
        )
    }

    @Test
    fun `a tiny claim deep inside a large one is rejected`() {
        // Touches no edge at all — an edge-intersection check would miss this
        // entirely, which is the reason containment needs testing separately.
        val estate = boxClaim("first", "Big Estate", 0, 0, 512, 512)

        assertNotNull(validate(box(240, 240, 272, 272), listOf(estate)))
    }

    @Test
    fun `a claim inside another but flush against one edge is rejected`() {
        // Shares a border AND ground. The shared edge must not excuse the
        // overlap — this is the case most likely to be waved through by a
        // "do they touch?" implementation.
        val estate = boxClaim("first", "Big Estate", 0, 0, 256, 256)

        assertNotNull(validate(box(0, 0, 64, 64), listOf(estate)))
    }

    @Test
    fun `a claim inside another and flush against two edges is rejected`() {
        // A corner pocket: two shared borders, still sitting on the estate's
        // ground.
        val estate = boxClaim("first", "Big Estate", 0, 0, 256, 256)

        assertNotNull(validate(box(192, 192, 256, 256), listOf(estate)))
    }

    @Test
    fun `a claim inside a concave arm is rejected`() {
        // Containment inside a non-convex shape. A convex-hull shortcut would
        // get the L-shape's notch wrong; this sits in the solid arm.
        val lShape = Claim(
            id = "l-shape",
            title = "L Shape",
            world = "world",
            createdByUuid = "eee00000-1111-2222-3333-444444444444",
            vertices = listOf(
                Vertex(0, 0),
                Vertex(256, 0),
                Vertex(256, 64),
                Vertex(64, 64),
                Vertex(64, 256),
                Vertex(0, 256),
            ),
            createdAt = "2026-08-15T12:00:00Z",
            updatedAt = "2026-08-15T12:00:00Z",
        )

        // Well inside the horizontal arm.
        assertNotNull(validate(box(128, 16, 160, 48), listOf(lShape)))
    }

    @Test
    fun `a claim in a concave notch is still accepted`() {
        // The counterpart to the test above: same L-shape, but this box sits in
        // the empty notch rather than on the arm. Containment must not become
        // "anything near a concave claim is rejected".
        val lShape = Claim(
            id = "l-shape",
            title = "L Shape",
            world = "world",
            createdByUuid = "eee00000-1111-2222-3333-444444444444",
            vertices = listOf(
                Vertex(0, 0),
                Vertex(256, 0),
                Vertex(256, 64),
                Vertex(64, 64),
                Vertex(64, 256),
                Vertex(0, 256),
            ),
            createdAt = "2026-08-15T12:00:00Z",
            updatedAt = "2026-08-15T12:00:00Z",
        )

        assertNull(
            validate(box(128, 128, 192, 192), listOf(lShape)),
            "a claim in the empty notch was wrongly rejected",
        )
    }

    @Test
    fun `containment is caught in either direction`() {
        val small = boxClaim("small", "Small Plot", 64, 64, 128, 128)
        val large = boxClaim("large", "Big Estate", 0, 0, 256, 256)

        // New claim inside an existing one...
        assertNotNull(validate(box(80, 80, 112, 112), listOf(large)))
        // ...and a new claim swallowing an existing one.
        assertNotNull(validate(box(0, 0, 256, 256), listOf(small)))
    }

    @Test
    fun `containment is detected against any claim in the store, not just the first`() {
        // The loop must keep looking. A claim landing inside the third of three
        // is just as invalid as one inside the first.
        val store = listOf(
            boxClaim("a", "First", 0, 0, 64, 64),
            boxClaim("b", "Second", 128, 0, 192, 64),
            boxClaim("c", "Third", 256, 0, 512, 256),
        )

        val error = validate(box(300, 50, 360, 110), store)
        assertNotNull(error)
        assertTrue("Third" in error, "wrong claim named: $error")
    }

    @Test
    fun `an edit may not swallow a neighbour`() {
        // Growing your own claim over someone else's is containment, not a
        // border dispute, and the self-exclusion must not hide it.
        val self = boxClaim("mine", "Mine", 0, 0, 64, 64)
        val neighbour = boxClaim("theirs", "Theirs", 128, 128, 192, 192)

        val error = validate(
            box(0, 0, 256, 256),
            listOf(self, neighbour),
            excludeClaimId = "mine",
        )
        assertNotNull(error)
        assertTrue("Theirs" in error, "wrong claim named: $error")
    }

    @Test
    fun `shape rules are checked before overlap`() {
        // A two-point shape cannot be made into a valid polygon, so the count
        // problem must be reported rather than a JTS failure surfacing.
        val existing = boxClaim("first", "Willowbrook Manor", 0, 0, 64, 64)

        val error = validate(listOf(Vertex(0, 0), Vertex(64, 64)), listOf(existing))
        assertNotNull(error)
        assertTrue("at least 3" in error, "wrong rule reported first: $error")
    }

    @Test
    fun `an empty store never reports an overlap`() {
        assertNull(validate(box(0, 0, 64, 64), emptyList()))
    }

    // ---- 2.5: world scoping -------------------------------------------------

    @Test
    fun `identical claims in different worlds are both accepted`() {
        // 2.5's stated verify. The Overworld and the Nether are separate
        // coordinate spaces, so the same x/z is not the same place.
        val overworld = boxClaim("ow", "Overworld Base", 0, 0, 64, 64, world = "world")
        val sameFootprint = box(0, 0, 64, 64)

        assertNull(
            validate(sameFootprint, listOf(overworld), world = "world_nether"),
            "a Nether claim was blocked by an Overworld claim at the same coordinates",
        )
    }

    @Test
    fun `the same footprint is valid in all three worlds at once`() {
        val footprint = box(0, 0, 64, 64)
        val existing = listOf(
            boxClaim("a", "Overworld", 0, 0, 64, 64, world = "world"),
            boxClaim("b", "Nether", 0, 0, 64, 64, world = "world_nether"),
        )

        assertNull(validate(footprint, existing, world = "world_the_end"))
    }

    @Test
    fun `overlap is still caught within the same world`() {
        // World scoping must narrow the check, not disable it.
        val nether = boxClaim("n", "Nether Fortress", 0, 0, 64, 64, world = "world_nether")

        val error = validate(box(32, 32, 96, 96), listOf(nether), world = "world_nether")
        assertNotNull(error)
        assertTrue("Nether Fortress" in error, "wrong claim named: $error")
    }

    @Test
    fun `containment across worlds is allowed`() {
        // A Nether claim entirely inside an Overworld one's footprint is not a
        // conflict — it is a different dimension.
        val estate = boxClaim("e", "Big Estate", 0, 0, 512, 512, world = "world")

        assertNull(validate(box(240, 240, 272, 272), listOf(estate), world = "world_the_end"))
    }

    @Test
    fun `the right world is picked out of a mixed store`() {
        val store = listOf(
            boxClaim("a", "Overworld Plot", 0, 0, 64, 64, world = "world"),
            boxClaim("b", "Nether Plot", 0, 0, 64, 64, world = "world_nether"),
            boxClaim("c", "End Plot", 0, 0, 64, 64, world = "world_the_end"),
        )

        // Overlaps all three footprints, but only the End one counts.
        val error = validate(box(32, 32, 96, 96), store, world = "world_the_end")
        assertNotNull(error)
        assertTrue("End Plot" in error, "matched a claim from the wrong world: $error")
    }

    @Test
    fun `an edit is not blocked by a same-coordinate claim in another world`() {
        val self = boxClaim("mine", "Mine", 0, 0, 64, 64, world = "world")
        val elsewhere = boxClaim("other", "Nether Twin", 0, 0, 128, 128, world = "world_nether")

        assertNull(
            validate(box(0, 0, 80, 80), listOf(self, elsewhere), excludeClaimId = "mine", world = "world"),
        )
    }

    @Test
    fun `world matching is exact, not a prefix`() {
        // "world" is a prefix of "world_nether"; a startsWith comparison would
        // wrongly treat them as the same space.
        val nether = boxClaim("n", "Nether Plot", 0, 0, 64, 64, world = "world_nether")

        assertNull(
            validate(box(0, 0, 64, 64), listOf(nether), world = "world"),
            "world names were compared loosely",
        )
    }

    private companion object {
        /** Matches the config.yml default, so the tests mirror a real server. */
        const val DEFAULT_CAP = 50
    }
}
