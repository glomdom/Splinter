package com.glomdom.splinter.extensions

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay

private val inputMarker = ItemStackBuilder.of(Material.LIGHT_BLUE_CONCRETE)
    .addCustomModelDataString("splinter:port_input")

private val outputMarker = ItemStackBuilder.of(Material.ORANGE_CONCRETE)
    .addCustomModelDataString("splinter:port_output")

fun DataEndpoint.faceMarker(stack: ItemStackBuilder, face: BlockFace): ItemDisplay =
    ItemDisplayBuilder().itemStack(stack).transformation {
        it.lookAlong(face.oppositeFace)
        it.translate(0.0, 0.0, -0.5)
        it.scale(0.25, 0.25, 0.1)
    }.build(block.location.toCenterLocation())

fun DataEndpoint.portMarker(kind: DataPort.Kind, face: BlockFace): ItemDisplay =
    faceMarker(if (kind == DataPort.Kind.INPUT) inputMarker else outputMarker, face)

fun DataEndpoint.addPortMarkers() {
    for ((face, port) in dataPorts) {
        addEntity("port_${face.name.lowercase()}", portMarker(port.kind, face))
    }
}
