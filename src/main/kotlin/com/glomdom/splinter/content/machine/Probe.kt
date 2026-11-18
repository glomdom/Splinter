package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.item.FilterSlot
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.util.addPortMarkers
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

class Probe : RebarBlock, HologramHolder, GuiRebarBlock, ReceiverLinked, DataEndpoint {
    override val link = ReceiverLink()

    override val dataPorts: Map<BlockFace, DataPort> =
        listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
            .associateWith { DataPort(this, it, DataPort.Kind.OUTPUT) }

    override val hologram = Hologram(this, Accent.PROBE)

    private val filter = FilterSlot { refreshValue() }

    private var lastValue: Long? = null

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        hologram.spawn()
        addPortMarkers()

        render()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        link.load(pdc)
        filter.load(pdc)
    }

    override fun write(pdc: PersistentDataContainer) {
        link.save(pdc)
        filter.save(pdc)
    }

    override fun postLoad() {
        hologram.restore()
        refreshValue()
    }

    override fun createGui(): Gui = filter.gui()
    override fun onLinkChanged() = refreshValue()
    override fun onWiringChanged() = render()

    fun refreshValue() {
        val value = filter.key?.let { key -> link.receiver?.count(key) }

        if (value != lastValue) {
            lastValue = value
            dataPorts.values.forEach { it.emit(value ?: 0) }
        }

        render()
    }

    private fun render() = hologram.render {
        val key = filter.key

        when {
            !isLinked -> status(Tone.WARN, "unlinked")
            key == null -> status(Tone.WARN, "no_filter")
            link.receiver == null -> status(Tone.IDLE, "receiver_unloaded")

            else -> status(Tone.GOOD, "measuring")
        }

        row("filter", key?.name?.color(Fmt.NUMBER) ?: Fmt.none())
        row("count", lastValue?.let { Fmt.number(it) })
        row("wired", Fmt.ratio(connectedPorts.toLong(), dataPorts.size.toLong()))
        row("receiver", link.target?.let(Fmt::position))
    }
}
