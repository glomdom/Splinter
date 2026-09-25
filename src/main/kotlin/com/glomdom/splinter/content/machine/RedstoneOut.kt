package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.extensions.REDSTONE_STRENGTH
import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.papermc.paper.event.block.TargetHitEvent
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class RedstoneOut : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, DataEndpoint {
    val inputFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.RED_CONCRETE)
        .addCustomModelDataString(key + ":inputFace")

    private var lastOutput = 0

    override val dataPorts by lazy {
        mapOf(facing to DataPort(this, facing, DataPort.Kind.INPUT))
    }

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = if (ctx.player?.isSneaking == true) {
            ctx.facing.oppositeFace
        } else {
            ctx.facing
        }

        addEntity("inputFace", ItemDisplayBuilder().itemStack(inputFaceStack).transformation {
            it.lookAlong(facing.oppositeFace)
            it.translate(0.0, 0.0, -0.5)
            it.scale(0.25, 0.25, 0.1)
        }.build(block.location.toCenterLocation()))

        addEntity("output_strength", label(block, 0.95))

        refreshOutputStrength()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    private fun refreshOutputStrength() {
        getHeldEntity(TextDisplay::class.java, "output_strength")?.text(
            UnitFormat.REDSTONE_STRENGTH.format(lastOutput).asComponent()
        )
    }

    override fun onInput(port: DataPort) {
        val data = block.blockData as? AnaloguePowerable ?: return

        data.power = port.value.coerceIn(0, 15).toInt()

        block.setBlockData(data, true)
        lastOutput = data.power

        refreshOutputStrength()
    }

    companion object : Listener {
        /**
         * Cancels target block's arrow hitting and powering it functionality
         */
        @Suppress("unused")
        @EventHandler(ignoreCancelled = true)
        private fun onTargetHit(e: TargetHitEvent) {
            if (BlockStorage.get(e.hitBlock ?: return) is RedstoneOut) {
                e.isCancelled = true
            }
        }
    }
}