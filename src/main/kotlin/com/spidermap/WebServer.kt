package com.spidermap

import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.logger.slf4j.ComponentLogger

/**
 * Embedded HTTP server. Serves the web UI and (from Milestone 1) the REST API
 * from the same origin, so the browser never needs CORS (SDLC §1).
 */
class WebServer(private val logger: ComponentLogger, private val port: Int) {

    private var app: Javalin? = null

    fun start() {
        // Deliberately does NOT swap the thread context classloader. Javalin's
        // JettyServer.start() begins by calling ServiceLoader.load() to look for
        // an SLF4J provider; pointing that at our plugin's classloader finds
        // nothing (Paper's provider lives in the parent) and Javalin then prints
        // a "you don't have a logger" banner to stderr, which Paper flags in
        // turn. Leaving the server's loader in place resolves the provider.
        app = Javalin.create { config ->
            config.startup.showJavalinBanner = false
            config.staticFiles.add { staticFiles ->
                staticFiles.hostedPath = "/"
                staticFiles.directory = "/web"
                staticFiles.location = Location.CLASSPATH
            }
        }.start(port)

        announceReady()
    }

    fun stop() {
        // Must release the port here — otherwise a plugin reload leaves the
        // socket bound and the next start fails with "address already in use".
        app?.stop()
        app = null
    }

    /**
     * The URL is the one thing an operator needs off the console, and it lands
     * in the middle of Paper's very noisy startup. ComponentLogger renders real
     * colour to the console rather than raw ANSI escapes.
     */
    private fun announceReady() {
        val url = "http://localhost:$port"

        logger.info(Component.empty())
        logger.info(
            Component.text("  spider-map ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("web UI is ready", NamedTextColor.GRAY))
        )
        logger.info(
            Component.text("  -> ", NamedTextColor.DARK_GRAY)
                .append(
                    Component.text(
                        url,
                        NamedTextColor.GREEN,
                        TextDecoration.BOLD,
                        TextDecoration.UNDERLINED,
                    )
                )
        )
        logger.info(Component.empty())
    }
}
