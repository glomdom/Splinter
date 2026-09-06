package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.content.machine.data.DataWire.Companion.MAX_LINE_LENGTH
import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksKey
import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksType
import com.glomdom.splinter.content.machine.data.DataWire.Companion.thicknessKey
import com.glomdom.splinter.content.machine.data.DataWire.Companion.thicknessType
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.interfaces.EntityGroupCulledRebarBlock
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay

class DataWireRenderer(private val wire: DataWire) {
    val faceGroups = mutableMapOf<BlockFace, EntityGroupCulledRebarBlock.EntityCullingGroup>()

    fun rebuild() {
        for (face in wire.connectedFaces) {
            (wire.connectedBlock(face) as? DataWire)?.let {
                it.getHeldEntity(DataDisplays.face(face))?.remove()
                it.getHeldEntity(DataDisplays.UNCONNECTED)?.remove()
                it.renderer.faceGroups.remove(face)
                it.renderer.faceGroups.remove(BlockFace.SELF)
            }
        }

        for (entity in wire.heldEntities.keys.toList()) {
            wire.getHeldEntity(entity)?.remove()
            wire.heldEntities.remove(entity)
        }

        faceGroups.clear()

        val faces = wire.connectedFaces
        if (faces.isEmpty()) {
            createNotConnectedDisplay(wire.block.location.toCenterLocation())

            faceGroups[BlockFace.SELF] = EntityGroupCulledRebarBlock.EntityCullingGroup("SELF").also {
                it.blocks.add(wire)
                it.entityIds.add(wire.getHeldEntityUuidOrThrow(DataDisplays.UNCONNECTED))
            }
        } else if (faces.size == DataWire.MAX_CONNECTIONS && faces[0] == faces[1].oppositeFace) {
            createWireDisplay(wire.findEndOfLine(faces[0]), wire.findEndOfLine(faces[1]), faces[0].oppositeFace)
        } else {
            createWireDisplay(wire.findEndOfLine(faces[0]), wire.block, faces[0].oppositeFace)

            if (faces.size == DataWire.MAX_CONNECTIONS) {
                createWireDisplay(wire.findEndOfLine(faces[1]), wire.block, faces[1].oppositeFace)
            }
        }
    }

    private fun createWireDisplay(from: Block, to: Block, fromToFace: BlockFace) {
        val availableThicknesses = DataLine.POOL.toMutableList()
        for (neighbour in listOfNotNull(wire.wireAt(from), wire.wireAt(to))) {
            neighbour.heldEntities.keys.forEach { name ->
                availableThicknesses.remove(
                    neighbour.getHeldEntity(name)?.persistentDataContainer?.get(thicknessKey, thicknessType)
                )
            }
        }

        val thickness = availableThicknesses.firstOrNull() ?: error("no free wire thickness at ${wire.block.position}")
        val inset = 0.5 + thickness / 2.0
        val spawnLocation = wire.block.location.toCenterLocation()

        val dir = fromToFace.direction
        val fromVec = from.location.toCenterLocation().subtract(spawnLocation).toVector()
        val toVec = to.location.toCenterLocation().subtract(spawnLocation).toVector()

        if (BlockStorage.getAs<DataEndpoint>(from) != null) fromVec.add(dir.clone().multiply(inset))
        if (BlockStorage.getAs<DataEndpoint>(to) != null) toVec.subtract(dir.clone().multiply(inset))

        val display = DataLine.build(
            at = spawnLocation,
            from = fromVec.toVector3d(),
            to = toVec.toVector3d(),
            thickness = thickness,
        )

        display.persistentDataContainer.set(thicknessKey, thicknessType, thickness)

        val associatedBlocks = mutableListOf<BlockPosition>()
        val cullingGroup = EntityGroupCulledRebarBlock.EntityCullingGroup(fromToFace.name)
        cullingGroup.entityIds.add(display.uniqueId)

        wire.wireAt(from)?.attach(fromToFace, display, cullingGroup)
        associatedBlocks.add(from.position)

        var current = from
        var count = 0
        while (true) {
            if (count > MAX_LINE_LENGTH) {
                throw RuntimeException("Loop in data wire logic update detected; please open a bug report and show this error")
            }

            count++

            current = current.getRelative(fromToFace)
            if (current == to) break

            wire.wireAt(current)?.let {
                it.attach(fromToFace, display, cullingGroup)
                it.attach(fromToFace.oppositeFace, display, cullingGroup)
            }

            associatedBlocks.add(current.position)
        }

        wire.wireAt(to)?.attach(fromToFace.oppositeFace, display, cullingGroup)
        associatedBlocks.add(to.position)

        display.persistentDataContainer.set(blocksKey, blocksType, associatedBlocks)
    }

    private fun createNotConnectedDisplay(center: Location) {
        val display = ItemDisplayBuilder()
            .transformation(
                TransformBuilder()
                    .scale(DataLine.POOL[0])
            )
            .itemStack(
                ItemStackBuilder.of(Material.RED_CONCRETE)
                    .addCustomModelDataString("${wire.key}:single")
            )
            .build(center)

        wire.addEntity(DataDisplays.UNCONNECTED, display)
    }

    private fun DataWire.attach(
        face: BlockFace,
        display: ItemDisplay,
        group: EntityGroupCulledRebarBlock.EntityCullingGroup,
    ) {
        addEntity(DataDisplays.face(face), display)
        renderer.faceGroups[face] = group

        if (this !in group.blocks) group.blocks.add(this)
    }
}