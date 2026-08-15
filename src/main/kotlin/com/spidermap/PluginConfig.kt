package com.spidermap

import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path

/**
 * Typed snapshot of config.yml.
 *
 * Everything that needs a setting reads it from here, so no value is repeated
 * as a literal anywhere else. Loaded once on enable — the fields are immutable,
 * so nothing can observe a half-changed configuration mid-run.
 */
class PluginConfig private constructor(
    val webPort: Int,
    val claimsFile: Path,
    val vertexCap: Int,
    val chunkSnapSize: Int,
) {

    /** One line, so a misconfigured server is obvious from the startup log. */
    fun describe(): String =
        "web-port=$webPort, claims-file=$claimsFile, " +
            "vertex-cap=$vertexCap, chunk-snap-size=$chunkSnapSize"

    companion object {

        private const val DEFAULT_WEB_PORT = 8080
        private const val DEFAULT_CLAIMS_FILE = "claims.json"
        private const val DEFAULT_VERTEX_CAP = 50
        private const val DEFAULT_CHUNK_SNAP_SIZE = 16

        fun load(plugin: JavaPlugin): PluginConfig {
            // Writes the bundled config.yml on first run, then leaves it alone.
            plugin.saveDefaultConfig()
            // Bukkit caches the config across reloads; re-read so an edited file
            // is picked up on restart rather than serving a stale copy.
            plugin.reloadConfig()

            val config = plugin.config
            val claimsFileName = config.getString("claims-file") ?: DEFAULT_CLAIMS_FILE

            return PluginConfig(
                webPort = config.getInt("web-port", DEFAULT_WEB_PORT),
                // Resolved against the plugin folder so the setting stays a bare
                // filename and cannot escape into an arbitrary path.
                claimsFile = plugin.dataFolder.toPath().resolve(claimsFileName),
                vertexCap = config.getInt("vertex-cap", DEFAULT_VERTEX_CAP),
                chunkSnapSize = config.getInt("chunk-snap-size", DEFAULT_CHUNK_SNAP_SIZE),
            )
        }
    }
}
