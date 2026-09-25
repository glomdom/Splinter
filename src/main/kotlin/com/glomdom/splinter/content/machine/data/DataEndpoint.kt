package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.event.DataConnectEvent
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.util.IMMEDIATE_FACES
import org.bukkit.block.BlockFace
import org.joml.Vector3d

interface DataEndpoint : DataNode {
    val dataPorts: Map<BlockFace, DataPort>

    override fun canConnect(face: BlockFace) = face in dataPorts

    fun onInput(port: DataPort) {}

    fun updateDirectlyConnectedFaces() {
        for (face in IMMEDIATE_FACES) {
            val myPort = dataPorts[face] ?: continue
            if (myPort.kind != DataPort.Kind.OUTPUT) continue
            if (getHeldEntity(DataDisplays.direct(face)) != null) continue

            val other = BlockStorage.get(block.getRelative(face))
            if (other !is DataEndpoint) continue
            if (other.dataPorts[face.oppositeFace]?.kind != DataPort.Kind.INPUT) continue
            if (!DataConnectEvent(this, other).callEvent()) continue

            val from = block.location.toCenterLocation()
            val to = other.block.location.toCenterLocation()

            val display = DataLine.build(
                at = from,
                from = Vector3d(),
                to = to.subtract(from).toVector().toVector3d(),
                thickness = DataLine.DIRECT,
            )

            addEntity(DataDisplays.direct(face), display)
        }
    }
}