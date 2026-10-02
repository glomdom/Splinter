package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.extensions.REDSTONE_STRENGTH
import com.glomdom.splinter.extensions.addPortMarkers
import com.glomdom.splinter.utilities.Labels
import com.glomdom.splinter.utilities.placementFacing
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.papermc.paper.event.block.TargetHitEvent
import org.bukkit.block.Block
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class RedstoneOut : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, DataEndpoint {
    override val dataPorts by lazy {
        mapOf(facing to DataPort(this, facing, DataPort.Kind.INPUT))
    }

    private val labels = Labels(this)
    private val power: Int
        get() = (block.blockData as? AnaloguePowerable)?.power ?: 0

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx)

        addPortMarkers()
        labels.create("output_strength")

        render()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    override fun postLoad() = render()
    override fun onInput(port: DataPort) {
        val data = block.blockData as? AnaloguePowerable ?: return

        data.power = port.value.coerceIn(0, 15).toInt()
        block.setBlockData(data, true)

        render()
    }

    private fun render() {
        labels["output_strength"] = UnitFormat.REDSTONE_STRENGTH.format(power).asComponent()
    }

    companion object : Listener {
        @Suppress("unused")
        @EventHandler(ignoreCancelled = true)
        private fun onTargetHit(e: TargetHitEvent) {
            if (BlockStorage.get(e.hitBlock ?: return) is RedstoneOut) {
                e.isCancelled = true
            }
        }
    }
}