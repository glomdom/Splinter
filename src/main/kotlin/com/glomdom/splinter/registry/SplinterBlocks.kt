package com.glomdom.splinter.registry

import com.glomdom.splinter.content.machine.Reader
import com.glomdom.splinter.content.machine.Receiver
import com.glomdom.splinter.content.machine.data.DataWire
import org.bukkit.Material

object SplinterBlocks : SplinterRegistry() {
    val READER by block<Reader>(Material.PINK_STAINED_GLASS)
    val RECEIVER by block<Receiver>(Material.GRAY_STAINED_GLASS)
    val DATA_WIRE by block<DataWire>(Material.STRUCTURE_VOID)
}