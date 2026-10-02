package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.content.machine.data.FilterSlot
import com.glomdom.splinter.content.machine.data.ReceiverLink
import com.glomdom.splinter.content.machine.data.ReceiverLinked
import com.glomdom.splinter.extensions.addPortMarkers
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.utilities.Labels
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

class Probe : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, ReceiverLinked, DataEndpoint {
    override val linkRange = 64 // todo: make this configurable
    override val link = ReceiverLink()

    override val dataPorts: Map<BlockFace, DataPort> = mapOf(
        BlockFace.EAST to DataPort(this, BlockFace.EAST, DataPort.Kind.OUTPUT),
        BlockFace.WEST to DataPort(this, BlockFace.WEST, DataPort.Kind.OUTPUT),
        BlockFace.NORTH to DataPort(this, BlockFace.NORTH, DataPort.Kind.OUTPUT),
        BlockFace.SOUTH to DataPort(this, BlockFace.SOUTH, DataPort.Kind.OUTPUT),
    )

    private val filter = FilterSlot { refreshValue() }
    private val labels = Labels(this)

    private var lastValue: Long? = null

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        labels.create("status", "filter", "value")
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

    override fun postLoad() = refreshValue()
    override fun createGui(): Gui = filter.gui()
    override fun onLinked(target: LinkTarget) {
        link.attach(target)

        refreshValue()
    }

    override fun onUnlinked(target: LinkTarget) {
        link.detach()

        refreshValue()
    }

    fun refreshValue() {
        val value = filter.key?.let { key -> link.receiver?.count(key) }

        if (value != lastValue) {
            lastValue = value
            dataPorts.values.forEach { it.emit(value ?: 0) }
        }

        render()
    }

    private fun render() {
        labels["status"] = if (isLinked) tr("probe.status.receiving") else tr("probe.status.unlinked")
        labels["filter"] = filter.label()
        labels["value"] = lastValue?.let { UnitFormat.ITEMS.format(it).asComponent() }
    }

    companion object : Listener
}