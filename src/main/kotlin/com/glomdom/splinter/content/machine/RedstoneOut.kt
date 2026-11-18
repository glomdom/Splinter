package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.papermc.paper.event.block.TargetHitEvent
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class RedstoneOut : RebarBlock, DirectionalRebarBlock, HologramHolder, DataEndpoint {
    override val dataPorts by lazy {
        mapOf(facing to DataPort(this, facing, DataPort.Kind.INPUT))
    }

    override val hologram = Hologram(this, Accent.REDSTONE_OUT)

    private val input get() = dataPorts.getValue(facing)

    private val power: Int
        get() = (block.blockData as? AnaloguePowerable)?.power ?: 0

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx)

        addPortMarkers()
        hologram.spawn()

        render()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    override fun postLoad() {
        hologram.restore()
        render()
    }

    override fun onWiringChanged() = render()

    override fun onInput(port: DataPort) {
        val data = block.blockData as? AnaloguePowerable ?: return

        data.power = port.value.coerceIn(0, MAX_POWER.toLong()).toInt()
        block.setBlockData(data, true)

        render()
    }

    private fun render() = hologram.render {
        when {
            !input.isConnected -> status(Tone.WARN, "not_wired")
            power > 0 -> status(Tone.GOOD, "powered")
            else -> status(Tone.IDLE, "off")
        }

        if (input.isConnected) {
            val value = input.value
            val clamped = value !in 0..MAX_POWER

            row(
                "input",
                if (clamped) Fmt.number(value).append(Component.space()).append(tr("hologram.clamped")) else Fmt.number(value)
            )
        }

        row("strength", Fmt.meter(power.toLong(), MAX_POWER.toLong(), Accent.REDSTONE_OUT, width = MAX_POWER))
    }

    companion object : Listener {
        private const val MAX_POWER = 15

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true)
        private fun onTargetHit(e: TargetHitEvent) {
            if (BlockStorage.get(e.hitBlock ?: return) is RedstoneOut) {
                e.isCancelled = true
            }
        }
    }
}
