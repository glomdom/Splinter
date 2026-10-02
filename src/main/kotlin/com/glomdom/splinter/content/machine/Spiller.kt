package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.content.machine.data.ReceiverLink
import com.glomdom.splinter.content.machine.data.ReceiverLinked
import com.glomdom.splinter.datatypes.ItemKeyType
import com.glomdom.splinter.extensions.addPortMarkers
import com.glomdom.splinter.extensions.left
import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.extensions.right
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Crafter
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem

class Spiller : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, GuiRebarBlock, TickingRebarBlock, LinkSource,
    ReceiverLinked, DataEndpoint {

    override val link = ReceiverLink()
    override val linkRange = 64

    override val dataPorts by lazy {
        val back = facing.oppositeFace
        val side = facing.left()

        mapOf(
            back to DataPort(this, back, DataPort.Kind.INPUT),
            side to DataPort(this, side, DataPort.Kind.OUTPUT),
        )
    }

    private val input
        get() = dataPorts.getValue(facing.oppositeFace)

    private val ready
        get() = dataPorts.getValue(facing.left())

    private var filterKey: ItemKey? = null
    private var filterItem: FilterItem? = null

    private var claims = mutableSetOf<Int>()
    private var status = Status.NO_FILTER
    private var has: Int? = null // null = target has no notion of the input

    private var lastRendered: RenderState? = null

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        val base = ctx.player?.facing ?: BlockFace.NORTH

        facing = if (ctx.player?.isSneaking == true) {
            base.oppositeFace
        } else {
            base
        }

        setTickInterval(10) // todo: make this tiered/configurable

        addPortMarkers()

        addEntity("status", label(block, 0.95))
        addEntity("filter", label(block, 0.825))
        addEntity("amounts", label(block, 0.7))
        addEntity("slots", label(block, 0.575))

        render()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        link.load(pdc)

        pdc.get(claimsKey, claimsType)?.let { claims = it.toMutableSet() }
        pdc.get(filterKeyKey, ItemKeyType)?.let { filterKey = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        link.save(pdc)

        pdc.set(claimsKey, claimsType, claims)
        filterKey?.let { pdc.set(filterKeyKey, ItemKeyType, it) }
    }

    override fun postLoad() = render()
    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # F # # # #")
            .addIngredient('F', FilterItem().also { filterItem = it })
            .addIngredient('#', GuiItems.background())
            .build()

    override fun onLinked(target: LinkTarget) {
        link.attach(target)

        tick()
    }

    override fun onUnlinked(target: LinkTarget) {
        link.detach()

        tick()
    }

    override fun onInput(port: DataPort) = render()
    override fun tick() {
        status = work()
        ready.emit(if (status == Status.SATISFIED) 1 else 0)

        render()
    }

    private fun work(): Status {
        val key = filterKey ?: return Status.NO_FILTER
        if (!isLinked) return Status.UNLINKED

        val keep = input.value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

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
                    // keep
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
        val taken = takeFromNetwork(key, 1) ?: return Status.NO_STOCK
        val existing = inv.getItem(slot)

        if (existing == null || existing.isEmpty) {
            inv.setItem(slot, taken)
        } else {
            inv.setItem(slot, existing.clone().apply { amount += 1 })
        }

        has = (has ?: 0) + 1

        return Status.SPILLING
    }

    private fun takeFromNetwork(key: ItemKey, amount: Int): ItemStack? {
        return link.receiver?.take(key, amount)
    }

    private fun render() {
        val state = RenderState(status, filterKey, input.value, has, claims.sorted())
        if (state == lastRendered) return

        lastRendered = state

        text("status", tr("spiller.status.${state.status.id}"))
        text(
            "filter",
            state.filter?.let { tr("spiller.filter.item", "item" to it.name) }
                ?: tr("spiller.filter.none")
        )

        text(
            "amounts", if (state.has == null) {
                tr("spiller.keep", "keep" to Component.text(state.keep))
            } else {
                tr("spiller.keep_has", "keep" to Component.text(state.keep), "has" to Component.text(state.has))
            }
        )

        text(
            "slots", if (state.slots.isEmpty()) {
                null
            } else {
                tr("spiller.slots", "slots" to Component.text(state.slots.joinToString(" ") { (it + 1).toString() }))
            }
        )
    }

    private fun text(name: String, component: Component?) {
        getHeldEntity(TextDisplay::class.java, name)?.text(component)
    }

    private fun setFilter(clickType: ClickType, player: Player) {
        val held = player.itemOnCursor
        val previous = filterKey

        if (clickType.isLeftClick && clickType.isShiftClick) {
            filterKey = null
        } else if (clickType.isLeftClick && !held.isEmpty) {
            filterKey = ItemKey.of(held)
        }

        if (filterKey != previous) {
            claims.clear()
            has = null

            filterItem?.refresh()
            tick()
        }
    }

    private fun getFilterStack(): ItemStack {
        val key = filterKey ?: return ItemStack.of(Material.BARRIER)
        val source = key.stack()

        return ItemStack.of(source.type).apply {
            setData(DataComponentTypes.ITEM_MODEL, source.getData(DataComponentTypes.ITEM_MODEL)!!)
        }
    }

    private inner class FilterItem : AbstractItem() {
        fun refresh() = notifyWindows()

        override fun getItemProvider(viewer: Player) =
            ItemStackBuilder.gui(getFilterStack(), "splinter:filter_item")
                .name(tr("gui.filter.name"))
                .lore(
                    filterKey?.let { tr("gui.filter.current.item", "name" to it.name) }
                        ?: tr("gui.filter.current.none"),
                    tr("gui.filter.hint")
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) =
            setFilter(clickType, player)
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

    private data class RenderState(
        val status: Status,
        val filter: ItemKey?,
        val keep: Long,
        val has: Int?,
        val slots: List<Int>,
    )

    companion object : Listener {
        private val claimsKey = splinterKey("spiller_claims")
        private val claimsType = RebarSerializers.SET.setTypeFrom(RebarSerializers.INTEGER)

        private val filterKeyKey = splinterKey("filter_key")
    }
}