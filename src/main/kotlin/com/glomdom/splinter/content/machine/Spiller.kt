package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.item.FilterSlot
import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.faceMarker
import com.glomdom.splinter.util.left
import com.glomdom.splinter.util.right
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock
import io.github.pylonmc.rebar.logistics.LogisticGroup
import io.github.pylonmc.rebar.logistics.LogisticGroupType
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Crafter
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

class Spiller : RebarBlock, DirectionalRebarBlock, HologramHolder, GuiRebarBlock, TickingRebarBlock,
    ReceiverLinked, DataEndpoint {

    override val link = ReceiverLink()
    override val hologram = Hologram(this, Accent.SPILLER)

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

    /** Crafter slots this spiller is responsible for. */
    private var claims = mutableSetOf<Int>()
    private var mode = Mode.PER_SLOT
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

        hologram.spawn()

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

    override fun postLoad() {
        hologram.restore()
        render()
    }

    override fun createGui(): Gui = filter.gui()
    override fun onLinkChanged() = tick()
    override fun onInput(port: DataPort) = render()
    override fun onWiringChanged() = render()

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

        val target = block.getRelative(facing)
        val crafter = target.getState(false) as? Crafter

        if (crafter != null && BlockStorage.get(target) == null) {
            mode = Mode.PER_SLOT
            return tickCrafter(crafter, key, keep)
        }

        mode = Mode.TOTAL
        claims.clear()

        return tickLogistic(target, key, keep)
    }

    private fun tickLogistic(target: Block, key: ItemKey, keep: Int): Status {
        // fixme: allow selection of logistic group to use instead of every input

        val template = key.stack()
        val slots = inputSlots(target, template)

        if (slots.isEmpty()) {
            has = null

            return Status.INVALID_TARGET
        }

        fun matches(slot: LogisticSlot) = slot.getItemStack()?.takeIf { !it.isEmpty }?.let { ItemKey.of(it) == key } == true
        fun isEmpty(slot: LogisticSlot) = slot.getItemStack()?.isEmpty ?: true

        val current = slots.filter(::matches).sumOf { it.getAmount() }
        has = current.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        if (keep == 0) return Status.IDLE
        if (current >= keep) return Status.SATISFIED

        val slot = (slots.filter(::matches) + slots.filter(::isEmpty)).firstOrNull {
            val next = it.getAmount() + 1

            next <= it.getMaxAmount(template) && it.canSet(template, next)
        } ?: return Status.TARGET_FULL

        val taken = takeFromNetwork(key, 1)
        if (taken == null) {
            short = (keep - current).toInt()

            return Status.NO_STOCK
        }

        val stack = slot.getItemStack()?.takeIf { !it.isEmpty }?.clone() ?: taken
        slot.set(stack, slot.getAmount() + 1)

        has = (has ?: 0) + 1

        return Status.SPILLING
    }

    private fun inputSlots(target: Block, item: ItemStack): List<LogisticSlot> {
        val groups = (BlockStorage.get(target) as? LogisticRebarBlock)?.getLogisticGroups()
            ?: LogisticGroup.getVanillaLogisticSlots(target)

        return groups.values
            .filter { it.slotType != LogisticGroupType.OUTPUT && it.filter?.invoke(item) != false }
            .flatMap { it.slots }
            .distinct()
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

    private fun render() = hologram.render {
        val keep = keepPort.value

        status(status.tone, status.id)

        row("filter", filter.key?.name?.color(Fmt.NUMBER) ?: Fmt.none())
        row("keep", if (keepPort.isConnected) tr(mode.keepKey, "count" to Fmt.number(keep)) else tr("hologram.not_wired"))

        if (mode == Mode.PER_SLOT && claims.isNotEmpty()) {
            row("slots", Component.text(claims.sorted().joinToString(" ") { (it + 1).toString() }, Accent.SPILLER))
        }

        has?.let { has ->
            val target = when (mode) {
                Mode.PER_SLOT -> keep * claims.size
                Mode.TOTAL -> keep
            }

            if (target > 0) {
                row("stocked", Fmt.meter(has.toLong(), target, Accent.SPILLER))
            } else {
                row("stocked", Fmt.number(has))
            }
        }

        if (short > 0) row("short", Fmt.number(short, NamedTextColor.RED))
    }

    private enum class Mode(val keepKey: String) {
        PER_SLOT("hologram.per_slot"),
        TOTAL("hologram.total"),
    }

    private enum class Status(val id: String, val tone: Tone) {
        UNLINKED("unlinked", Tone.WARN),
        NO_FILTER("no_filter", Tone.WARN),
        INVALID_TARGET("invalid_target", Tone.BAD),
        NO_PATTERN("no_pattern", Tone.WARN),
        IDLE("idle", Tone.IDLE),
        SATISFIED("satisfied", Tone.GOOD),
        SPILLING("spilling", Tone.ACTIVE),
        NO_STOCK("no_stock", Tone.BAD),
        TARGET_FULL("target_full", Tone.BAD),
    }

    companion object {
        private val claimsKey = splinterKey("spiller_claims")
        private val claimsType = RebarSerializers.SET.setTypeFrom(RebarSerializers.INTEGER)

        private val TARGET_MARKER = Markers.marker(Material.BLUE_CONCRETE, "spiller_target")
        private val KEEP_MARKER = Markers.marker(Material.LIME_CONCRETE, "spiller_keep")
        private val SHORT_MARKER = Markers.marker(Material.RED_CONCRETE, "spiller_short")
    }
}