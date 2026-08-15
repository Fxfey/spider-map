package com.spidermap

import org.bukkit.plugin.java.JavaPlugin

class SpiderMapPlugin : JavaPlugin() {

    private var webServer: WebServer? = null

    override fun onEnable() {
        // Step 0.3 replaces this with a proper config object; reading it here
        // keeps the port out of the code from the start.
        val port = config.getInt("web-port", DEFAULT_WEB_PORT)

        try {
            webServer = WebServer(componentLogger, port).apply { start() }
        } catch (e: Exception) {
            // A bound port would otherwise leave the plugin "enabled" but with
            // no web UI, which is a confusing state to debug. Fail loudly.
            logger.severe("Could not start the web server on port $port: ${e.message}")
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

    private companion object {
        const val DEFAULT_WEB_PORT = 8080
    }
}
