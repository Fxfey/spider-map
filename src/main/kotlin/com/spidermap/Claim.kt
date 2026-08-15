package com.spidermap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single claim corner, in Minecraft world coordinates.
 *
 * Integers rather than doubles: corners snap to a 16-block grid (SDLC §2), so
 * a fractional coordinate would mean something upstream had gone wrong.
 */
@Serializable
data class Vertex(
    val x: Int,
    val z: Int,
)

/**
 * A land claim, matching the JSON schema in SDLC §4.
 *
 * Field names are snake_case on the wire and camelCase in Kotlin, mapped
 * explicitly with @SerialName so renaming a property here can never silently
 * change the stored format.
 */
@Serializable
data class Claim(
    val id: String,
    val title: String,

    /**
     * Bukkit world name — `world`, `world_nether` or `world_the_end`.
     * Every position check filters on this first: the Nether is a separate
     * coordinate space, so the same x/z there is not the same place (SDLC §2).
     */
    val world: String,

    /** Null for unowned claims such as Spawn; the announcement then drops the owner line. */
    @SerialName("owner_uuid") val ownerUuid: String? = null,

    /** Denormalised cache of the owner's username — `ownerUuid` is the source of truth. */
    @SerialName("owner_name") val ownerName: String? = null,

    /** The editor who drew it. Kept for accountability; no gameplay effect. */
    @SerialName("created_by_uuid") val createdByUuid: String,

    val vertices: List<Vertex>,

    /** Bumped on every save; a mismatched value rejects the write (SDLC §4). */
    val version: Int = 1,

    /** ISO-8601 UTC. Held as a String so stored timestamps round-trip byte-identically. */
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
