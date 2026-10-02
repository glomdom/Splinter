package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.content.machine.Receiver
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class ReceiverLink {
    var target: BlockPosition? = null
        private set

    val isLinked: Boolean
        get() = target != null

    val receiver: Receiver?
        get() = target
            ?.takeIf { it.isChunkLoaded }
            ?.let { BlockStorage.getAs<Receiver>(it) }

    fun attach(to: LinkTarget) {
        target = to.block.position
    }

    fun detach() {
        target = null
    }

    fun load(pdc: PersistentDataContainer) {
        pdc.get(key, RebarSerializers.BLOCK_POSITION)?.let { target = it }
    }

    fun save(pdc: PersistentDataContainer) {
        target?.let { pdc.set(key, RebarSerializers.BLOCK_POSITION, it) }
    }

    companion object {
        private val key = splinterKey("linked_receiver")
    }
}

interface ReceiverLinked : LinkSource {
    val link: ReceiverLink

    override val isLinked: Boolean
        get() = link.isLinked
}

object ReceiverLinkListener : Listener {
    @Suppress("unused")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun onBreak(e: RebarBlockBreakEvent) {
        val source = e.rebarBlock as? ReceiverLinked ?: return

        source.link.receiver?.removeSource(source)
    }
}
