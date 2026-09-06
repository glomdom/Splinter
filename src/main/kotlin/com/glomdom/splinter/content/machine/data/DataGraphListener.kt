package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksKey
import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksType
import com.glomdom.splinter.event.DataDisconnectEvent
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent

/**
 * Listener for the graph functionality of data wires/related to them.
 */
object DataGraphListener : Listener {
    @EventHandler
    private fun onBreak(event: RebarBlockBreakEvent) {
        val endpoint = event.rebarBlock as? DataEndpoint ?: return

        for (face in endpoint.dataPorts.keys) {
            val neighbour = BlockStorage.get(endpoint.block.getRelative(face))

            if (neighbour is DataEndpoint && face.oppositeFace in neighbour.dataPorts) {
                val name = DataDisplays.direct(face.oppositeFace)
                neighbour.getHeldEntity(name)?.remove()
                neighbour.heldEntities.remove(name)

                DataDisconnectEvent(neighbour, endpoint).callEvent()
                neighbour.updateDirectlyConnectedFaces()
            }

            BlockStorage.getAs<DataWire>(endpoint.block.getRelative(face))?.let { wire ->
                if (face.oppositeFace in wire.connectedFaces) {
                    wire.connectedFaces.remove(face.oppositeFace)
                    wire.updateConnectedFaces()

                    DataDisconnectEvent(wire, endpoint).callEvent()
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private fun onPlace(event: RebarBlockPlaceEvent) {
        val endpoint = event.rebarBlock as? DataEndpoint ?: return

        endpoint.updateDirectlyConnectedFaces()

        for (face in endpoint.dataPorts.keys) {
            val neighbour = endpoint.block.getRelative(face)

            BlockStorage.getAs<DataEndpoint>(neighbour)?.updateDirectlyConnectedFaces()
            BlockStorage.getAs<DataWire>(neighbour)?.updateConnectedFaces()
        }
    }

    @EventHandler
    private fun onEntityRemove(event: EntityRemoveEvent) {
        if (event.cause == EntityRemoveEvent.Cause.UNLOAD || event.cause == EntityRemoveEvent.Cause.PLAYER_QUIT) return

        val blockPositions = event.entity.persistentDataContainer.get(blocksKey, blocksType) ?: return
        for (blockPos in blockPositions) {
            val block = BlockStorage.get(blockPos) as? DataWire ?: continue

            block.heldEntities.entries.removeIf { it.value == event.entity.uniqueId }
        }
    }
}