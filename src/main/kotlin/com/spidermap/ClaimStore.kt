package com.spidermap

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes the claims file — a single JSON array of [Claim] (SDLC §3).
 *
 * Deliberately has no in-memory cache: every [load] hits disk, so a test can
 * prove a round-trip by loading through a fresh instance. Callers hold the list.
 *
 * Writes are NOT yet atomic — that is step 1.2.
 */
class ClaimStore(private val file: Path) {

    /** A missing or empty file is a valid empty store, not an error. */
    fun load(): List<Claim> {
        if (!Files.exists(file)) return emptyList()

        val text = Files.readString(file)
        if (text.isBlank()) return emptyList()

        return json.decodeFromString(SERIALIZER, text)
    }

    fun save(claims: List<Claim>) {
        // The plugin folder exists by the time this runs, but a test may point
        // at a fresh temp directory.
        file.parent?.let { Files.createDirectories(it) }

        Files.writeString(file, json.encodeToString(SERIALIZER, claims))
    }

    private companion object {

        val SERIALIZER = ListSerializer(Claim.serializer())

        val json = Json {
            // The file is meant to be readable and diffable by a server admin.
            prettyPrint = true
            // Tolerate fields written by a newer build rather than refusing to
            // load the whole file and losing every claim.
            ignoreUnknownKeys = true
            // Without this, a claim with version 1 would omit the field, and a
            // reader without the same default could not tell what it meant.
            encodeDefaults = true
        }
    }
}
