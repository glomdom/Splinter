package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.event.DataConnectEvent
import com.glomdom.splinter.event.DataDisconnectEvent
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockBreakContext
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.EntityGroupCulledRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.FacadeRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.EntityStorage
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.IMMEDIATE_FACES
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.persistence.PersistentDataContainer

// huge thanks to https://github.com/pylonmc/rebar/blob/master/rebar/src/main/kotlin/io/github/pylonmc/rebar/content/cargo/CargoDuct.kt

class DataWire : RebarBlock, BlockBreakRebarBlockHandler, EntityHolderRebarBlock, EntityGroupCulledRebarBlock,
    FacadeRebarBlock, DataNode {

    var connectedFaces = mutableListOf<BlockFace>()
    val renderer = DataWireRenderer(this)

    override val cullingGroups
        get() = renderer.faceGroups.values

    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        updateConnectedFaces()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(connectedFacesKey, connectedFacesType)?.let { connectedFaces = it.toMutableList() }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(connectedFacesKey, connectedFacesType, connectedFaces)
    }

    override fun postLoad() {
        for (face in connectedFaces) {
            val displayId = getHeldEntityUuid(DataDisplays.face(face)) ?: continue
            EntityStorage.whenEntityLoads(displayId) { display: ItemDisplay ->
                if (renderer.faceGroups.containsKey(face)) {
                    return@whenEntityLoads
                }

                val cullingGroup = EntityGroupCulledRebarBlock.EntityCullingGroup(face.name)
                cullingGroup.entityIds.add(display.uniqueId)

                val blockPositions =
                    display.persistentDataContainer.get(blocksKey, blocksType) ?: return@whenEntityLoads

                for (blockPos in blockPositions) {
                    val block = BlockStorage.get(blockPos) as? DataWire ?: continue

                    block.renderer.faceGroups[face] = cullingGroup
                    cullingGroup.blocks.add(block)
                }
            }
        }
    }

    override fun onPostBlockBreak(context: BlockBreakContext) {
        for (face in connectedFaces) {
            when (val neighbour = connectedBlock(face)) {
                is DataWire -> {
                    neighbour.connectedFaces.remove(face.oppositeFace)
                    neighbour.updateConnectedFaces()

                    DataDisconnectEvent(this, neighbour).callEvent()
                }

                is DataEndpoint -> DataDisconnectEvent(this, neighbour).callEvent()
            }
        }
    }

    override fun canConnect(face: BlockFace) =
        connectedFaces.size < MAX_CONNECTIONS && face !in connectedFaces

    fun updateConnectedFaces() {
        if (connectedFaces.size == MAX_CONNECTIONS) return

        val candidates = IMMEDIATE_FACES
            .mapNotNull { face ->
                val adjacent = BlockStorage.get(block.getRelative(face)) as? DataNode ?: return@mapNotNull null
                if (face in connectedFaces) return@mapNotNull null
                if (!adjacent.canConnect(face.oppositeFace)) return@mapNotNull null

                face to adjacent
            }
            .sortedBy { (_, node) ->
                when (node) {
                    is DataEndpoint -> 0
                    is DataWire if node.connectedFaces.size == 1 -> 1
                    else -> 2
                }
            }

        for ((face, node) in candidates) {
            if (connectedFaces.size == MAX_CONNECTIONS) break

            if (!node.canConnect(face.oppositeFace)) continue
            if (!DataConnectEvent(this, node).callEvent()) continue

            connectedFaces.add(face)

            if (node is DataWire) node.connectedFaces.add(face.oppositeFace)
        }

        renderer.rebuild()
    }

    fun connectedBlock(face: BlockFace): RebarBlock? {
        if (face !in connectedFaces) {
            return null
        }

        return BlockStorage.get(block.getRelative(face))
    }

    fun findEndOfLine(face: BlockFace): Block {
        var currentDuct = this
        var count = 0

        while (true) {
            if (count > MAX_LINE_LENGTH) {
                throw RuntimeException("Loop in data wire line detected at ${block.position}; please open a bug report and show this error")
            }

            count++

            val nextBlock = currentDuct.connectedBlock(face) ?: return currentDuct.block
            if (nextBlock is DataWire) {
                currentDuct = nextBlock
                continue
            }

            if (nextBlock is DataEndpoint) return nextBlock.block

            return currentDuct.block
        }
    }

    fun wireAt(b: Block): DataWire? =
        if (b == block) this else BlockStorage.getAs<DataWire>(b)

    companion object : Listener {
        const val MAX_LINE_LENGTH = 1000
        const val MAX_CONNECTIONS = 2

        val connectedFacesKey = splinterKey("connected_faces")
        val connectedFacesType = RebarSerializers.LIST.listTypeFrom(RebarSerializers.BLOCK_FACE)

        val blocksKey = splinterKey("blocks")
        val blocksType = RebarSerializers.LIST.listTypeFrom(RebarSerializers.BLOCK_POSITION)

        val thicknessKey = splinterKey("thickness")
        val thicknessType = RebarSerializers.FLOAT
    }
}