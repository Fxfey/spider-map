package com.spidermap.landclaims

import org.bukkit.plugin.java.JavaPlugin

class LandClaimsPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("SpiderMap v${pluginMeta.version} enabled")
    }

    override fun onDisable() {
        logger.info("SpiderMap v${pluginMeta.version} disabled")
    }
}
