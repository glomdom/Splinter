package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.item.FilterSlot
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.faceMarker
import com.glomdom.splinter.util.left
import com.glomdom.splinter.util.right
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Crafter
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

/**
 * Keeps the inventory it faces (currently only crafters) stocked with the filtered item,
 * pulling from the linked receiver's network one item per tick.
 *
 * Ports: back = how many to keep (input), left = 1 when satisfied, right = how many items the network is short.
 */
class Spiller : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, GuiRebarBlock, TickingRebarBlock,
    ReceiverLinked, DataEndpoint {

    override val link = ReceiverLink()

    override val dataPorts by lazy {
        val back = facing.oppositeFace
        val left = facing.left()
        val right = facing.right()

        mapOf(
            back to DataPort(this, back, DataPort.Kind.INPUT),
            left to DataPort(this, left, DataPort.Kind.OUTPUT),
            right to DataPort(this, right, DataPort.Kind.OUTPUT),
        )
    }

    private val keepPort get() = dataPorts.getValue(facing.oppositeFace)
    private val readyPort get() = dataPorts.getValue(facing.left())
    private val shortPort get() = dataPorts.getValue(facing.right())

    private val filter = FilterSlot { onFilterChanged() }
    private val labels = Labels(this)

    /** Crafter slots this spiller is responsible for (slots whose pattern matches the filter). */
    private var claims = mutableSetOf<Int>()
    private var status = Status.NO_FILTER
    private var has: Int? = null // null = target has no notion of the input
    private var short = 0

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx, horizontal = true)

        setTickInterval(10) // todo: make this tiered/configurable

        addEntity("targetFace", faceMarker(TARGET_MARKER, facing))
        addPortMarkers(
            mapOf(
                facing.oppositeFace to KEEP_MARKER,
                facing.right() to SHORT_MARKER,
            )
        )

        labels.create("status", "filter", "amounts", "slots")

        render()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        link.load(pdc)
        filter.load(pdc)

        pdc.get(claimsKey, claimsType)?.let { claims = it.toMutableSet() }
    }

    override fun write(pdc: PersistentDataContainer) {
        link.save(pdc)
        filter.save(pdc)

        pdc.set(claimsKey, claimsType, claims)
    }

    override fun postLoad() = render()
    override fun createGui(): Gui = filter.gui()
    override fun onLinkChanged() = tick()
    override fun onInput(port: DataPort) = render()

    override fun tick() {
        short = 0
        status = work()

        readyPort.emit(if (status == Status.SATISFIED) 1 else 0)
        shortPort.emit(short.toLong())

        render()
    }

    private fun work(): Status {
        val key = filter.key ?: return Status.NO_FILTER
        if (!isLinked) return Status.UNLINKED

        val keep = keepPort.value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

        return when (val target = block.getRelative(facing).getState(false)) {
            is Crafter -> {
                tickCrafter(target, key, keep)
            }

            else -> {
                has = null
                Status.INVALID_TARGET
            }
        }
    }

    private fun tickCrafter(crafter: Crafter, key: ItemKey, keep: Int): Status {
        val inv = crafter.inventory

        for (slot in 0 until 9) {
            val stack = inv.getItem(slot)

            when {
                crafter.isSlotDisabled(slot) -> {
                    claims -= slot
                }

                stack == null || stack.isEmpty -> {
                    // keep the claim, an emptied slot is still ours
                }

                ItemKey.of(stack) == key -> {
                    claims += slot
                }

                else -> {
                    claims -= slot
                }
            }
        }

        fun amountAt(slot: Int) = inv.getItem(slot)?.amount ?: 0
        fun maxAt(slot: Int) = inv.getItem(slot)?.takeIf { !it.isEmpty }?.maxStackSize ?: 64

        if (claims.isEmpty()) {
            has = null
            return Status.NO_PATTERN
        }

        has = claims.sumOf(::amountAt)

        if (keep == 0) return Status.IDLE

        val below = claims.filter { amountAt(it) < keep }
        if (below.isEmpty()) return Status.SATISFIED

        val slot = below
            .filter { amountAt(it) < maxAt(it) }
            .minByOrNull(::amountAt)
            ?: return Status.TARGET_FULL

        // todo: make this tierable/configurable as well
        val taken = takeFromNetwork(key, 1)
        if (taken == null) {
            short = below.sumOf { keep - amountAt(it) }

            return Status.NO_STOCK
        }

        val existing = inv.getItem(slot)

        if (existing == null || existing.isEmpty) {
            inv.setItem(slot, taken)
        } else {
            inv.setItem(slot, existing.clone().apply { amount += 1 })
        }

        has = (has ?: 0) + 1

        return Status.SPILLING
    }

    private fun takeFromNetwork(key: ItemKey, amount: Int): ItemStack? =
        link.receiver?.take(key, amount)

    private fun onFilterChanged() {
        claims.clear()
        has = null

        tick()
    }

    private fun render() {
        val keep = Component.text(keepPort.value)

        labels["status"] = tr("spiller.status.${status.id}")
        labels["filter"] = filter.label()

        labels["amounts"] = has?.let { tr("spiller.keep_has", "keep" to keep, "has" to Component.text(it)) }
            ?: tr("spiller.keep", "keep" to keep)

        labels["slots"] = claims
            .takeIf { it.isNotEmpty() }
            ?.sorted()
            ?.let { tr("spiller.slots", "slots" to Component.text(it.joinToString(" ") { slot -> (slot + 1).toString() })) }
    }

    private enum class Status(val id: String) {
        UNLINKED("unlinked"),
        NO_FILTER("no_filter"),
        INVALID_TARGET("invalid_target"),
        NO_PATTERN("no_pattern"),
        IDLE("idle"),
        SATISFIED("satisfied"),
        SPILLING("spilling"),
        NO_STOCK("no_stock"),
        TARGET_FULL("target_full"),
    }

    companion object {
        private val claimsKey = splinterKey("spiller_claims")
        private val claimsType = RebarSerializers.SET.setTypeFrom(RebarSerializers.INTEGER)

        private val TARGET_MARKER = Markers.marker(Material.BLUE_CONCRETE, "spiller_target")
        private val KEEP_MARKER = Markers.marker(Material.LIME_CONCRETE, "spiller_keep")
        private val SHORT_MARKER = Markers.marker(Material.RED_CONCRETE, "spiller_short")
    }
}