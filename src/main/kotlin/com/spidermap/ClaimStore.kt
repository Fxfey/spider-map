package com.spidermap

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes the claims file — a single JSON array of [Claim] (SDLC §3).
 *
 * Deliberately has no in-memory cache: every [load] hits disk, so a test can
 * prove a round-trip by loading through a fresh instance. Callers hold the list.
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
        writeAtomically { json.encodeToString(SERIALIZER, claims) }
    }

    /**
     * Writes to a temp file in the same directory, then renames it over the
     * target. The rename is the only operation that touches [file], and it
     * either happens or it doesn't — so a crash mid-write leaves the previous
     * claims intact rather than a truncated file (SDLC §3).
     *
     * The temp file must be a sibling: a rename across filesystems is a
     * copy-then-delete, which is exactly the non-atomic behaviour being avoided.
     *
     * `internal` rather than private so a test can inject a failure between
     * "start writing" and "commit" — the only way to verify the guarantee
     * without actually killing the JVM.
     */
    internal fun writeAtomically(content: () -> String) {
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)

        val temp = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            Files.writeString(temp, content())
            moveIntoPlace(temp)
        } finally {
            // No-op on the success path — the move already consumed it. This
            // clears the temp file when serialising or writing threw.
            Files.deleteIfExists(temp)
        }
    }

    private fun moveIntoPlace(temp: Path) {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            // Some filesystems (certain network shares) cannot rename
            // atomically. A replacing move still beats writing in place, and
            // failing the save outright would be worse than a narrower window.
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {

        /**
         * Distinctive enough that a leftover from a crashed write is obviously
         * ours, and suffixed so it can never be mistaken for the real file.
         */
        const val TEMP_PREFIX = "claims-"
        const val TEMP_SUFFIX = ".tmp"

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
