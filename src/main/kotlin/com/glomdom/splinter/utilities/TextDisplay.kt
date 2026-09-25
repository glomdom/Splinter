package com.glomdom.splinter.utilities

import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import org.bukkit.Color
import org.bukkit.block.Block
import org.bukkit.entity.Display

fun label(block: Block, y: Double) =
    TextDisplayBuilder()
        .transformation(TransformBuilder().translate(0.0, y, 0.0).scale(0.6))
        .billboard(Display.Billboard.VERTICAL)
        .backgroundColor(Color.fromARGB(0))
        .build(block.location.toCenterLocation())