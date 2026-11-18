package com.glomdom.splinter.util

import org.bukkit.block.BlockFace

/** The face to the left when looking along this (horizontal) face. */
fun BlockFace.left() = when (this) {
    BlockFace.NORTH -> BlockFace.WEST
    BlockFace.WEST -> BlockFace.SOUTH
    BlockFace.SOUTH -> BlockFace.EAST
    BlockFace.EAST -> BlockFace.NORTH

    else -> error("left() is only defined for horizontal faces, got $this")
}

/** The face to the right when looking along this (horizontal) face. */
fun BlockFace.right() = left().oppositeFace
