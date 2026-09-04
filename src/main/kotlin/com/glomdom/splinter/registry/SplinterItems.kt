package com.glomdom.splinter.registry

import com.glomdom.splinter.content.tool.LinkerTool
import com.glomdom.splinter.guide.SplinterPages
import org.bukkit.Material

object SplinterItems : SplinterRegistry() {
    val READER by blockItem(Material.PINK_STAINED_GLASS, SplinterPages.SPLINTER)
    val RECEIVER by blockItem(Material.GRAY_STAINED_GLASS, SplinterPages.SPLINTER)

    val LINKER_TOOL by item<LinkerTool>(Material.BRUSH, SplinterPages.SPLINTER)
}