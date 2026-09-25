package com.glomdom.splinter.extensions

import org.bukkit.block.BlockFace

fun BlockFace.left() = when (this) {
    BlockFace.NORTH -> BlockFace.WEST
    BlockFace.WEST -> BlockFace.SOUTH
    BlockFace.SOUTH -> BlockFace.EAST
    BlockFace.EAST -> BlockFace.NORTH

    else -> error("compare is horizontal only")
}

fun BlockFace.right() = when (this) {
    BlockFace.NORTH -> BlockFace.EAST
    BlockFace.EAST -> BlockFace.SOUTH
    BlockFace.SOUTH -> BlockFace.WEST
    BlockFace.WEST -> BlockFace.NORTH

    else -> error("compare is horizontal only")
}