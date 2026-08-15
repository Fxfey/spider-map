package com.spidermap

/**
 * Geometry rules a claim must satisfy before it can be saved (SDLC §2).
 *
 * Returns the first failure as a human-readable message, or null when the shape
 * is acceptable. The message reaches the browser directly, so it is phrased for
 * whoever is drawing the claim rather than for a log.
 *
 * Later steps in Milestone 2 add self-intersection, minimum area, and the
 * overlap check — each as its own rule here.
 */
class ClaimValidator(private val vertexCap: Int) {

    fun validate(vertices: List<Vertex>): String? = when {
        vertices.size < MINIMUM_VERTICES ->
            "a claim needs at least $MINIMUM_VERTICES points, but this one has ${vertices.size}"

        vertices.size > vertexCap ->
            "a claim can have at most $vertexCap points, but this one has ${vertices.size}"

        else -> null
    }

    private companion object {
        /**
         * Three is the fewest points that can enclose any area at all. Two
         * describe a line, which has no inside for a player to stand in.
         */
        const val MINIMUM_VERTICES = 3
    }
}
