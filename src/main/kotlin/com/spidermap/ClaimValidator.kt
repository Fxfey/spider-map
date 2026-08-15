package com.spidermap

/**
 * Geometry rules a claim must satisfy before it can be saved (SDLC §2).
 *
 * Returns the first failure as a human-readable message, or null when the shape
 * is acceptable. The message reaches the browser directly, so it is phrased for
 * whoever is drawing the claim rather than for a log.
 *
 * Later steps in Milestone 2 add minimum area and the overlap check.
 */
class ClaimValidator(private val vertexCap: Int) {

    fun validate(vertices: List<Vertex>): String? = when {
        vertices.size < MINIMUM_VERTICES ->
            "a claim needs at least $MINIMUM_VERTICES points, but this one has ${vertices.size}"

        vertices.size > vertexCap ->
            "a claim can have at most $vertexCap points, but this one has ${vertices.size}"

        selfIntersects(vertices) ->
            "the outline crosses itself — a claim has to be a simple shape, " +
                "so move the points so no two edges overlap"

        else -> null
    }

    /**
     * True when any two edges that don't share a corner touch or cross.
     *
     * Blocked rather than warned about (SDLC §2): a bowtie has no coherent
     * inside, so the point-in-polygon test the announcements rely on would give
     * arbitrary answers. Better to fail at draw time than to misbehave later.
     *
     * O(n²), which is nothing at the 50-point cap.
     */
    private fun selfIntersects(vertices: List<Vertex>): Boolean {
        val edgeCount = vertices.size

        for (i in 0 until edgeCount) {
            for (j in i + 1 until edgeCount) {
                // Edges sharing a corner always "touch" there, which is what
                // being a polygon means. The last edge wraps round to meet the
                // first, so that pair is adjacent too.
                val adjacent = j == i + 1 || (i == 0 && j == edgeCount - 1)
                if (adjacent) continue

                val intersects = segmentsIntersect(
                    vertices[i], vertices[(i + 1) % edgeCount],
                    vertices[j], vertices[(j + 1) % edgeCount],
                )
                if (intersects) return true
            }
        }

        return false
    }

    private fun segmentsIntersect(a1: Vertex, a2: Vertex, b1: Vertex, b2: Vertex): Boolean {
        val d1 = cross(b1, b2, a1)
        val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1)
        val d4 = cross(a1, a2, b2)

        // Each segment straddles the other's line — a clean crossing.
        val straddles = ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        if (straddles) return true

        // Collinear or endpoint-touching. Counted as intersecting: an edge
        // clipping a far-off corner is just as broken as a full crossing.
        return (d1 == 0L && within(b1, b2, a1)) ||
            (d2 == 0L && within(b1, b2, a2)) ||
            (d3 == 0L && within(a1, a2, b1)) ||
            (d4 == 0L && within(a1, a2, b2))
    }

    /**
     * Z-component of the cross product of (a→b) and (a→c): positive, negative
     * or zero for a left turn, right turn, or collinear.
     *
     * Long, not Int: coordinates run to ±30 million at the world border, and
     * multiplying two 60-million spans overflows a 32-bit int — which would
     * silently flip the sign and report a crossing that isn't there.
     */
    private fun cross(a: Vertex, b: Vertex, c: Vertex): Long =
        (b.x - a.x).toLong() * (c.z - a.z).toLong() -
            (b.z - a.z).toLong() * (c.x - a.x).toLong()

    /** Whether collinear point [p] actually lies on segment a–b, not past its ends. */
    private fun within(a: Vertex, b: Vertex, p: Vertex): Boolean =
        p.x in minOf(a.x, b.x)..maxOf(a.x, b.x) &&
            p.z in minOf(a.z, b.z)..maxOf(a.z, b.z)

    private companion object {
        /**
         * Three is the fewest points that can enclose any area at all. Two
         * describe a line, which has no inside for a player to stand in.
         */
        const val MINIMUM_VERTICES = 3
    }
}
