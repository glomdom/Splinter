package com.glomdom.splinter.extensions

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.block.BlockFace

private val inputMarker = ItemStackBuilder.of(Material.LIGHT_BLUE_CONCRETE)
    .addCustomModelDataString("splinter:port_input")

private val outputMarker = ItemStackBuilder.of(Material.ORANGE_CONCRETE)
    .addCustomModelDataString("splinter:port_output")

fun RebarBlock.faceMarker(stack: ItemStackBuilder, face: BlockFace) =
    ItemDisplayBuilder().itemStack(stack).transformation {
        it.lookAlong(face.oppositeFace)
        it.translate(0.0, 0.0, -0.5)
        it.scale(0.25, 0.25, 0.1)
    }.build(block.location.toCenterLocation())

fun <T> T.addPortMarkers(overrides: Map<BlockFace, ItemStackBuilder> = emptyMap())
        where T : RebarBlock, T : DataEndpoint {
    for ((face, port) in dataPorts) {
        val stack = overrides[face] ?: if (port.kind == DataPort.Kind.INPUT) inputMarker else outputMarker

        addEntity("port_${face.name.lowercase()}", faceMarker(stack, face))
    }
}

