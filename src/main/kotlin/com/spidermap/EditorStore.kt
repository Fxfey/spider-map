package com.spidermap

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Logger

/**
 * The map-editor list: who may create, edit and delete claims.
 *
 * Self-contained by design (SDLC §2) — no LuckPerms, no Vault, nothing beyond a
 * bare Paper install. Stored separately from claims.json so that granting
 * someone editor rights can never touch claim data.
 *
 * Editors act on players' behalf: being an editor is not the same as owning a
 * claim, and an editor may assign any claim to any player.
 */
class EditorStore(private val file: Path, private val logger: Logger) {

    fun isEditor(uuid: UUID): Boolean = load().contains(uuid)

    /** A missing file is an empty list, not an error — same rule as [ClaimStore]. */
    @Synchronized
    fun load(): Set<UUID> {
        if (!Files.exists(file)) return emptySet()

        val text = Files.readString(file)
        if (text.isBlank()) return emptySet()

        return json.decodeFromString(SERIALIZER, text)
            .mapNotNull { raw ->
                // One malformed entry — a hand-edited file, say — must not cost
                // every other editor their access.
                runCatching { UUID.fromString(raw) }
                    .onFailure { logger.warning("Ignoring malformed editor UUID in ${file.fileName}: '$raw'") }
                    .getOrNull()
            }
            .toSet()
    }

    /** @return false when they were already an editor, so the caller can say so. */
    @Synchronized
    fun add(uuid: UUID): Boolean {
        val current = load()
        if (current.contains(uuid)) return false

        save(current + uuid)
        return true
    }

    /** @return false when they weren't an editor to begin with. */
    @Synchronized
    fun remove(uuid: UUID): Boolean {
        val current = load()
        if (!current.contains(uuid)) return false

        save(current - uuid)
        return true
    }

    /**
     * Temp file then rename, so a crash mid-write leaves the previous list
     * intact rather than a truncated file that locks every editor out.
     *
     * Deliberately a small copy of [ClaimStore]'s approach rather than a shared
     * helper: extracting one would mean reworking ClaimStore and its tests in a
     * step that is about editors. Worth extracting when a third store appears.
     */
    private fun save(editors: Set<UUID>) {
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)

        val temp = Files.createTempFile(directory, "editors-", ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(SERIALIZER, editors.map(UUID::toString)))
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private companion object {
        val SERIALIZER = ListSerializer(String.serializer())

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}
