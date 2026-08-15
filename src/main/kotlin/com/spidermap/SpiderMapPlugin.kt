package com.spidermap

import org.bukkit.plugin.java.JavaPlugin

class SpiderMapPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("spider-map v${pluginMeta.version} enabled")
    }

    override fun onDisable() {
        logger.info("spider-map v${pluginMeta.version} disabled")
    }
}
