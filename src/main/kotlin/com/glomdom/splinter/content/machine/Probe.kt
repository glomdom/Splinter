package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.item.FilterSlot
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

/** Outputs (on every horizontal side) how many of the filtered item the linked receiver's network holds. */
class Probe : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, ReceiverLinked, DataEndpoint {
    override val link = ReceiverLink()

    override val dataPorts: Map<BlockFace, DataPort> =
        listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
            .associateWith { DataPort(this, it, DataPort.Kind.OUTPUT) }

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
    override fun onLinkChanged() = refreshValue()

    /** Re-reads the count from the receiver; called by the receiver whenever its contents change. */
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
}
