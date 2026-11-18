package com.glomdom.splinter.content.tool

import com.glomdom.splinter.link.LinkResult
import com.glomdom.splinter.link.Linkable
import com.glomdom.splinter.link.Linking
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.RebarBlock.Companion.rebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.interfaces.BlockInteractRebarItemHandler
import io.github.pylonmc.rebar.item.interfaces.InteractRebarItemHandler
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * Right-click a [Linkable] to start, then right-click its partner to link/unlink them.
 * The first block's location is stored on the item stack until the link completes or is cancelled.
 */
class LinkerTool(stack: ItemStack) : RebarItem(stack), BlockInteractRebarItemHandler, InteractRebarItemHandler {
    override fun onInteractWithBlock(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.hand != EquipmentSlot.HAND || !event.action.isRightClick) return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        val player = event.player
        if (player.isSneaking) return clearPending(player)

        val clicked = event.clickedBlock?.rebarBlock as? Linkable
            ?: return player.sendActionBar(LinkResult.InvalidBlock.message)

        val pending = pendingLocation() ?: return beginLink(player, clicked)
        if (!pending.isChunkLoaded) return clearPending(player)

        val first = pending.block.rebarBlock as? Linkable ?: return clearPending(player)
        val result = Linking.link(first, clicked)

        player.sendActionBar(result.message)

        if (result.succeeded) clearPending(player, notify = false)
    }

    override fun onInteract(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.hand != EquipmentSlot.HAND || event.clickedBlock != null) return
        if (event.player.isSneaking) clearPending(event.player)
    }

    private fun pendingLocation(): Location? =
        stack.persistentDataContainer.get(PENDING_KEY, RebarSerializers.LOCATION)

    private fun beginLink(player: Player, target: Linkable) {
        stack.editMeta { it.persistentDataContainer.set(PENDING_KEY, RebarSerializers.LOCATION, target.block.location) }

        player.sendActionBar(
            tr(
                "messages.linker.started_linking",
                "what" to target.displayName,
                "x" to Component.text(target.block.x),
                "y" to Component.text(target.block.y),
                "z" to Component.text(target.block.z),
            )
        )
    }

    private fun clearPending(player: Player, notify: Boolean = true) {
        if (pendingLocation() == null) return

        stack.editMeta { it.persistentDataContainer.remove(PENDING_KEY) }

        if (notify) player.sendActionBar(tr("messages.linker.stopped_linking"))
    }

    companion object {
        private val PENDING_KEY = splinterKey("pending_link")
    }
}
