package com.glomdom.splinter.registry

import com.glomdom.splinter.content.machine.Constant
import com.glomdom.splinter.content.machine.Probe
import com.glomdom.splinter.content.machine.Reader
import com.glomdom.splinter.content.machine.Receiver
import com.glomdom.splinter.content.machine.RedstoneOut
import com.glomdom.splinter.content.machine.data.DataWire
import org.bukkit.Material

object SplinterBlocks : SplinterRegistry() {
    val READER by block<Reader>(Material.PINK_STAINED_GLASS)
    val RECEIVER by block<Receiver>(Material.GRAY_STAINED_GLASS)
    val PROBE by block<Probe>(Material.RED_STAINED_GLASS)
    val CONSTANT by block<Constant>(Material.GREEN_STAINED_GLASS)

    val REDSTONE_OUT by block<RedstoneOut>(Material.TARGET)

    val DATA_WIRE by block<DataWire>(Material.STRUCTURE_VOID)
}