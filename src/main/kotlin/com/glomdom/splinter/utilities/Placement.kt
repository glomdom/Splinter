package com.glomdom.splinter.utilities

import io.github.pylonmc.rebar.block.context.BlockCreateContext
import org.bukkit.block.BlockFace

fun placementFacing(ctx: BlockCreateContext, horizontal: Boolean = false): BlockFace {
    val base = if (horizontal) {
        ctx.player?.facing ?: BlockFace.NORTH
    } else {
        ctx.facing
    }

    return if (ctx.player?.isSneaking == true) {
        base.oppositeFace
    } else {
        base
    }
}