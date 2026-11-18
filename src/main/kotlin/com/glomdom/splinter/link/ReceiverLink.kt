package com.glomdom.splinter.link

import com.glomdom.splinter.content.machine.Receiver
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

/** Persisted pointer from a source block to the [Receiver] it is linked to. */
class ReceiverLink {
    var target: BlockPosition? = null
        private set

    val isLinked: Boolean
        get() = target != null

    /** The linked receiver, or `null` if unlinked or its chunk isn't loaded. */
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

/**
 * A block that can be linked to a [Receiver] with the linker tool.
 *
 * Implementors hold a [ReceiverLink] (remember to [ReceiverLink.load]/[ReceiverLink.save] it) and
 * override [onLinkChanged] to react to linking/unlinking.
 */
interface ReceiverLinked : LinkSource {
    val link: ReceiverLink

    override val linkRange: Int
        get() = DEFAULT_LINK_RANGE // todo: make this configurable

    override val isLinked: Boolean
        get() = link.isLinked

    override fun onLinked(target: LinkTarget) {
        link.attach(target)
        onLinkChanged()
    }

    override fun onUnlinked(target: LinkTarget) {
        link.detach()
        onLinkChanged()
    }

    /** Called after the block was linked to or unlinked from a receiver. */
    fun onLinkChanged() {}

    companion object {
        const val DEFAULT_LINK_RANGE = 64
    }
}

/** Removes a source from its receiver when the source is broken. */
object ReceiverLinkListener : Listener {
    @Suppress("unused")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun onBreak(e: RebarBlockBreakEvent) {
        val source = e.rebarBlock as? ReceiverLinked ?: return

        source.link.receiver?.removeSource(source)
    }
}
