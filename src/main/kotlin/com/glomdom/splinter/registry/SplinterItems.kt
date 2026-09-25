package com.glomdom.splinter.registry

import com.glomdom.splinter.content.tool.LinkerTool
import com.glomdom.splinter.guide.SplinterPages
import org.bukkit.Material

object SplinterItems : SplinterRegistry() {
    val READER by blockItem(Material.PINK_STAINED_GLASS, SplinterPages.SPLINTER)
    val RECEIVER by blockItem(Material.GRAY_STAINED_GLASS, SplinterPages.SPLINTER)
    val PROBE by blockItem(Material.RED_STAINED_GLASS, SplinterPages.SPLINTER)
    val REDSTONE_OUT by blockItem(Material.TARGET, SplinterPages.SPLINTER)

    val DATA_WIRE by blockItem(Material.STRUCTURE_VOID, Material.RED_CONCRETE, SplinterPages.SPLINTER)

    val LINKER_TOOL by item<LinkerTool>(Material.BRUSH, SplinterPages.SPLINTER)
}