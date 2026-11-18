package com.glomdom.splinter.util

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.block.BlockFace

/** Small colored squares drawn on block faces to show what each side does. */
object Markers {
    val INPUT: ItemStackBuilder = marker(Material.LIGHT_BLUE_CONCRETE, "port_input")
    val OUTPUT: ItemStackBuilder = marker(Material.ORANGE_CONCRETE, "port_output")

    /** Input "A" of a two-input block (left side). */
    val INPUT_A: ItemStackBuilder = marker(Material.BLUE_CONCRETE, "inputAFace")

    /** Input "B" of a two-input block (right side). */
    val INPUT_B: ItemStackBuilder = marker(Material.RED_CONCRETE, "inputBFace")

    fun marker(material: Material, model: String): ItemStackBuilder =
        ItemStackBuilder.of(material).addCustomModelDataString("splinter:$model")
}

fun RebarBlock.faceMarker(stack: ItemStackBuilder, face: BlockFace) =
    ItemDisplayBuilder().itemStack(stack).transformation {
        it.lookAlong(face.oppositeFace)
        it.translate(0.0, 0.0, -0.5)
        it.scale(0.25, 0.25, 0.1)
    }.build(block.location.toCenterLocation())

/** Adds an input/output marker on every port face, unless [overrides] supplies a custom one. */
fun <T> T.addPortMarkers(overrides: Map<BlockFace, ItemStackBuilder> = emptyMap())
        where T : RebarBlock, T : DataEndpoint {
    for ((face, port) in dataPorts) {
        val stack = overrides[face] ?: if (port.kind == DataPort.Kind.INPUT) Markers.INPUT else Markers.OUTPUT

        addEntity("port_${face.name.lowercase()}", faceMarker(stack, face))
    }
}
