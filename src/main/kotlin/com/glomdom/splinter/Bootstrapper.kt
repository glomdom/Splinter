package com.glomdom.splinter

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage", "unused")
class Bootstrapper : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {}
    override fun createPlugin(context: PluginProviderContext): JavaPlugin = Splinter
}