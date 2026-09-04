package com.glomdom.splinter

import com.glomdom.splinter.content.machine.Reader
import com.glomdom.splinter.content.machine.Receiver
import com.glomdom.splinter.guide.SplinterHelpPages
import com.glomdom.splinter.guide.SplinterPages
import com.glomdom.splinter.registry.SplinterBlocks
import com.glomdom.splinter.registry.SplinterItems
import com.glomdom.splinter.registry.SplinterRegistry
import io.github.pylonmc.rebar.addon.RebarAddon
import io.github.pylonmc.rebar.content.guide.RebarGuide
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

object Splinter : JavaPlugin(), RebarAddon {
    override fun onEnable() {
        registerWithRebar()

        SplinterItems.registerAll()
        SplinterBlocks.registerAll()
        SplinterHelpPages
        RebarGuide.rootPage.addPage(material, SplinterPages.SPLINTER)
        SplinterRegistry.validateClaims()

        val pm = server.pluginManager
        pm.registerEvents(Reader.Companion, this)
        pm.registerEvents(Receiver.Companion, this)
    }

    override val javaPlugin = this
    override val defaultLanguage: Locale = Locale.ENGLISH
    override val material = Material.REDSTONE
}

@JvmSynthetic
internal fun splinterKey(key: String): NamespacedKey =
    NamespacedKey(Splinter, key)