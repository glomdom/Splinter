package com.glomdom.splinter.interfaces

import io.github.pylonmc.rebar.block.RebarBlock
import net.kyori.adventure.text.Component
import org.bukkit.block.Block

interface Linkable {
    val displayName: Component
        get() = (this as RebarBlock).nameTranslationKey

    val block: Block
}