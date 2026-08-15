package com.spidermap

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimStoreTest {

    private fun tempClaimsFile(): Path =
        Files.createTempDirectory("spider-map-test").resolve("claims.json")

    private fun sampleClaim(
        id: String = "b3f1e2a0-0000-0000-0000-000000000001",
        ownerUuid: String? = "0f3d1111-2222-3333-4444-555555555555",
        ownerName: String? = "Nictorious",
    ) = Claim(
        id = id,
        title = "Willowbrook Manor",
        world = "world",
        ownerUuid = ownerUuid,
        ownerName = ownerName,
        createdByUuid = "eee00000-1111-2222-3333-444444444444",
        vertices = listOf(
            Vertex(x = 128, z = -64),
            Vertex(x = 144, z = -64),
            Vertex(x = 144, z = -48),
            Vertex(x = 128, z = -48),
        ),
        version = 1,
        createdAt = "2026-08-15T12:00:00Z",
        updatedAt = "2026-08-15T12:00:00Z",
    )

    @Test
    fun `claim round-trips through json with no data loss`() {
        val file = tempClaimsFile()
        val original = sampleClaim()

        ClaimStore(file).save(listOf(original))

        // A separate instance, so this cannot pass on a cached in-memory copy.
        val loaded = ClaimStore(file).load()

        assertEquals(1, loaded.size)
        assertEquals(original, loaded.single())
    }

    @Test
    fun `nested vertices survive the round trip in order`() {
        val file = tempClaimsFile()
        val original = sampleClaim()

        ClaimStore(file).save(listOf(original))
        val loaded = ClaimStore(file).load().single()

        // Winding order decides what the polygon actually is, so order matters
        // as much as the values.
        assertEquals(original.vertices, loaded.vertices)
        assertEquals(Vertex(128, -64), loaded.vertices.first())
        assertEquals(Vertex(128, -48), loaded.vertices.last())
    }

    @Test
    fun `unowned claim keeps null owner fields`() {
        val file = tempClaimsFile()
        val spawn = sampleClaim(ownerUuid = null, ownerName = null).copy(title = "Spawn")

        ClaimStore(file).save(listOf(spawn))
        val loaded = ClaimStore(file).load().single()

        // Must stay null rather than becoming "null" or "" — the announcement
        // drops the owner line based on this (SDLC §2).
        assertNull(loaded.ownerUuid)
        assertNull(loaded.ownerName)
        assertEquals("Spawn", loaded.title)
    }

    @Test
    fun `missing file loads as an empty store`() {
        // 1.3 expects GET /api/claims to return [] on a fresh install.
        val file = tempClaimsFile()
        assertEquals(emptyList(), ClaimStore(file).load())
    }

    @Test
    fun `multiple claims all survive`() {
        val file = tempClaimsFile()
        val claims = listOf(
            sampleClaim(id = "aaaa0000-0000-0000-0000-000000000001"),
            sampleClaim(id = "bbbb0000-0000-0000-0000-000000000002").copy(world = "world_nether"),
        )

        ClaimStore(file).save(claims)
        val loaded = ClaimStore(file).load()

        assertEquals(claims, loaded)
        assertEquals(setOf("world", "world_nether"), loaded.map { it.world }.toSet())
    }

    @Test
    fun `stored file uses the snake_case field names from the spec`() {
        val file = tempClaimsFile()
        ClaimStore(file).save(listOf(sampleClaim()))

        val text = Files.readString(file)

        // The web UI and any external tooling read this file, so the wire format
        // is part of the contract - not an implementation detail.
        assertTrue("owner_uuid" in text, "expected owner_uuid in:\n$text")
        assertTrue("created_by_uuid" in text, "expected created_by_uuid in:\n$text")
        assertTrue("created_at" in text, "expected created_at in:\n$text")
        assertTrue("updated_at" in text, "expected updated_at in:\n$text")
    }
}
