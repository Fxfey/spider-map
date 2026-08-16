package com.spidermap

import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import java.time.Duration

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
    val webLoginCodeLifetime: Duration,
    val sessionLifetime: Duration,
    /**
     * What players should open to reach the UI — not necessarily where the
     * server listens. Behind a reverse proxy those differ, and the port the
     * plugin binds may not be reachable from outside at all.
     */
    val publicUrl: String,
) {

    /** One line, so a misconfigured server is obvious from the startup log. */
    fun describe(): String =
        "web-port=$webPort, claims-file=$claimsFile, " +
            "vertex-cap=$vertexCap, chunk-snap-size=$chunkSnapSize, " +
            "weblogin-code-minutes=${webLoginCodeLifetime.toMinutes()}, " +
            "session-hours=${sessionLifetime.toHours()}, public-url=$publicUrl"

    companion object {

        private const val DEFAULT_WEB_PORT = 8080
        private const val DEFAULT_CLAIMS_FILE = "claims.json"
        private const val DEFAULT_VERTEX_CAP = 50
        private const val DEFAULT_CHUNK_SNAP_SIZE = 16
        private const val DEFAULT_WEBLOGIN_CODE_MINUTES = 30L
        private const val DEFAULT_SESSION_HOURS = 12L

        fun load(plugin: JavaPlugin): PluginConfig {
            // Writes the bundled config.yml on first run, then leaves it alone.
            plugin.saveDefaultConfig()
            // Bukkit caches the config across reloads; re-read so an edited file
            // is picked up on restart rather than serving a stale copy.
            plugin.reloadConfig()

            val config = plugin.config
            val claimsFileName = config.getString("claims-file") ?: DEFAULT_CLAIMS_FILE
            val webPort = config.getInt("web-port", DEFAULT_WEB_PORT)

            return PluginConfig(
                webPort = webPort,
                // Resolved against the plugin folder so the setting stays a bare
                // filename and cannot escape into an arbitrary path.
                claimsFile = plugin.dataFolder.toPath().resolve(claimsFileName),
                vertexCap = config.getInt("vertex-cap", DEFAULT_VERTEX_CAP),
                chunkSnapSize = config.getInt("chunk-snap-size", DEFAULT_CHUNK_SNAP_SIZE),
                // Floored at one second: zero or negative would expire every
                // code the instant it was issued, making web login impossible
                // in a way that looks like a bug rather than a setting.
                webLoginCodeLifetime = Duration.ofMinutes(
                    config.getLong("weblogin-code-minutes", DEFAULT_WEBLOGIN_CODE_MINUTES)
                        .coerceAtLeast(1L),
                ),
                sessionLifetime = Duration.ofHours(
                    config.getLong("session-hours", DEFAULT_SESSION_HOURS).coerceAtLeast(1L),
                ),
                // Blank means "same machine as the server", which is right for
                // single-player testing and wrong for everyone else — hence
                // the setting rather than a hardcoded guess.
                publicUrl = config.getString("public-url")
                    ?.trim()
                    ?.trimEnd('/')
                    ?.takeIf { it.isNotEmpty() }
                    ?: "http://localhost:$webPort",
            )
        }
    }
}
