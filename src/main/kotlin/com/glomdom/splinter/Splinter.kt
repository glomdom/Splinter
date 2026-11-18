package com.glomdom.splinter

import com.glomdom.splinter.content.machine.Reader
import com.glomdom.splinter.content.machine.Receiver
import com.glomdom.splinter.content.machine.RedstoneOut
import com.glomdom.splinter.data.DataGraphListener
import com.glomdom.splinter.guide.SplinterPages
import com.glomdom.splinter.hologram.HologramListener
import com.glomdom.splinter.link.ReceiverLinkListener
import com.glomdom.splinter.registry.SplinterBlocks
import com.glomdom.splinter.registry.SplinterItems
import com.glomdom.splinter.registry.SplinterRegistry
import io.github.pylonmc.rebar.addon.RebarAddon
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

object Splinter : JavaPlugin(), RebarAddon {
    override val javaPlugin = this
    override val defaultLanguage: Locale = Locale.ENGLISH
    override val material = Material.REDSTONE

    override fun onEnable() {
        registerWithRebar()

        SplinterItems.registerAll()
        SplinterBlocks.registerAll()
        SplinterPages.registerAll()
        SplinterRegistry.validateClaims()

        listOf(
            Reader.Companion,
            Receiver.Companion,
            RedstoneOut.Companion,
            DataGraphListener,
            ReceiverLinkListener,
            HologramListener,
        ).forEach { server.pluginManager.registerEvents(it, this) }
    }
}

@JvmSynthetic
internal fun splinterKey(key: String): NamespacedKey =
    NamespacedKey(Splinter, key)
