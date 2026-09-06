package com.glomdom.splinter.content.machine.data

import org.bukkit.block.BlockFace

object DataDisplays {
    const val PREFIX = "splinter:data_wire"
    const val UNCONNECTED = "$PREFIX:unconnected"

    fun face(face: BlockFace) = "$PREFIX:face:${face.name}"
    fun direct(face: BlockFace) = "$PREFIX:direct:${face.name}"
}