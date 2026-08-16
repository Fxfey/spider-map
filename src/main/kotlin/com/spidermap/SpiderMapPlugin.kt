package com.spidermap

import org.bukkit.plugin.java.JavaPlugin

class SpiderMapPlugin : JavaPlugin() {

    private var webServer: WebServer? = null
    private lateinit var pluginConfig: PluginConfig

    override fun onEnable() {
        pluginConfig = PluginConfig.load(this)
        // Logged so a config edit can be confirmed from the console without
        // opening the file (checklist 0.3 verify).
        logger.info("Config: ${pluginConfig.describe()}")

        val claimStore = ClaimStore(pluginConfig.claimsFile)

        // Sits beside claims.json in the plugin folder, deliberately separate:
        // granting editor rights must never touch claim data (SDLC §4).
        val editorStore = EditorStore(dataFolder.toPath().resolve("editors.json"), logger)

        // In memory: a login code lives for minutes, so surviving a restart
        // would buy nothing and would mean writing credentials to disk.
        val loginCodes = LoginCodeStore(pluginConfig.webLoginCodeLifetime)

        getCommand("claims")?.let { command ->
            val executor = ClaimsCommand(
                editorCommand = EditorCommand(editorStore, server),
                webLoginCommand = WebLoginCommand(
                    codes = loginCodes,
                    editors = editorStore,
                    publicUrl = pluginConfig.publicUrl,
                    codeLifetime = pluginConfig.webLoginCodeLifetime,
                ),
            )
            command.setExecutor(executor)
            command.tabCompleter = executor
        } ?: logger.severe("Command 'claims' is missing from plugin.yml — commands are unavailable")

        try {
            webServer = WebServer(
                logger = componentLogger,
                port = pluginConfig.webPort,
                claimRoutes = ClaimRoutes(
                    store = claimStore,
                    validator = ClaimValidator(vertexCap = pluginConfig.vertexCap),
                ),
            ).apply { start() }
        } catch (e: Exception) {
            // A bound port would otherwise leave the plugin "enabled" but with
            // no web UI, which is a confusing state to debug. Fail loudly.
            logger.severe("Could not start the web server on port ${pluginConfig.webPort}: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info("spider-map v${pluginMeta.version} enabled")
    }

    override fun onDisable() {
        webServer?.stop()
        webServer = null
        logger.info("spider-map v${pluginMeta.version} disabled")
    }
}
