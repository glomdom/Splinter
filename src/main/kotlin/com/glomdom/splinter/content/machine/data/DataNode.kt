package com.glomdom.splinter.content.machine.data

import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import org.bukkit.block.BlockFace

interface DataNode : EntityHolderRebarBlock {
    fun canConnect(face: BlockFace): Boolean
//    fun onGraphChanged()
}