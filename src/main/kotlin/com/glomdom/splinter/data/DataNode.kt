package com.glomdom.splinter.data

import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import org.bukkit.block.BlockFace

/** Anything that participates in the data-wire graph: either a [DataWire] or a [DataEndpoint]. */
interface DataNode : EntityHolderRebarBlock {
    fun canConnect(face: BlockFace): Boolean
}
