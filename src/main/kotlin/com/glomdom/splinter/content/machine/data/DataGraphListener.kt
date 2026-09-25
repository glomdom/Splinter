package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksKey
import com.glomdom.splinter.content.machine.data.DataWire.Companion.blocksType
import com.glomdom.splinter.event.DataConnectEvent
import com.glomdom.splinter.event.DataDisconnectEvent
import io.github.pylonmc.rebar.Rebar
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent
import io.github.pylonmc.rebar.util.delayTicks
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent

/**
 * Listener for the graph functionality of data wires/related to them.
 */
object DataGraphListener : Listener {
    fun relink(endpoint: DataEndpoint) {
        for (port in endpoint.dataPorts.values) {
            val far = DataWire.trace(endpoint.block, port.face)
                ?.let { (other, face) -> other.dataPorts[face] }
                ?.takeIf { it.kind != port.kind }

            if (far === port.peer) continue
            if (far == null) {
                port.unlink()
            } else {
                port.link(far)
            }
        }
    }

    @Suppress("unused")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun onConnect(e: DataConnectEvent) = relinkLater(e.block1, e.block2)

    @Suppress("unused")
    @EventHandler(priority = EventPriority.MONITOR)
    private fun onDisconnect(e: DataDisconnectEvent) = relinkLater(e.block1, e.block2)

    @Suppress("unused")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
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

    @Suppress("unused")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun onPlace(event: RebarBlockPlaceEvent) {
        val endpoint = event.rebarBlock as? DataEndpoint ?: return

        endpoint.updateDirectlyConnectedFaces()

        for (face in endpoint.dataPorts.keys) {
            val neighbour = endpoint.block.getRelative(face)

            BlockStorage.getAs<DataEndpoint>(neighbour)?.updateDirectlyConnectedFaces()
            BlockStorage.getAs<DataWire>(neighbour)?.updateConnectedFaces()
        }
    }

    @Suppress("unused")
    @EventHandler(priority = EventPriority.MONITOR)
    private fun onEntityRemove(event: EntityRemoveEvent) {
        if (event.cause == EntityRemoveEvent.Cause.UNLOAD || event.cause == EntityRemoveEvent.Cause.PLAYER_QUIT) return

        val blockPositions = event.entity.persistentDataContainer.get(blocksKey, blocksType) ?: return
        for (blockPos in blockPositions) {
            val block = BlockStorage.get(blockPos) as? DataWire ?: continue

            block.heldEntities.entries.removeIf { it.value == event.entity.uniqueId }
        }
    }

    @Suppress("unused")
    @EventHandler(priority = EventPriority.MONITOR)
    private fun onLoad(e: RebarBlockLoadEvent) {
        val endpoint = e.rebarBlock as? DataEndpoint ?: return

        relinkLater(endpoint)
    }

    private fun relinkLater(vararg nodes: DataNode) {
        Rebar.scope.launch(Rebar.mainThreadDispatcher) {
            delayTicks(1)

            for (node in nodes) {
                when (node) {
                    is DataEndpoint -> {
                        relink(node)
                    }

                    is DataWire -> {
                        for (face in node.connectedFaces) {
                            DataWire.trace(node.block, face)?.first?.let(::relink)
                        }
                    }
                }
            }
        }
    }
}