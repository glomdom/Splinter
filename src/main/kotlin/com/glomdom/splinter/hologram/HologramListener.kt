package com.glomdom.splinter.hologram

import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent

object HologramListener : Listener {
    @Suppress("unused")
    @EventHandler(priority = EventPriority.MONITOR)
    private fun onEntitiesLoad(e: EntitiesLoadEvent) {
        for (entity in e.entities) {
            if (entity !is TextDisplay) continue

            val pos = entity.persistentDataContainer
                .get(EntityHolderRebarBlock.blockKey, RebarSerializers.BLOCK_POSITION) ?: continue

            val holder = BlockStorage.get(pos) as? HologramHolder ?: continue

            when (entity.uniqueId) {
                holder.hologram.entityId -> holder.hologram.flush(force = true)

                !in holder.heldEntities.values -> entity.remove()
            }
        }
    }
}
