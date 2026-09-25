package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.datatypes.ItemKeyType
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
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

class Probe : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, LinkSource, DataEndpoint {
    private var linkedTo: BlockPosition? = null
    private var filterKey: ItemKey? = null

    private var filterItem: FilterItem? = null
    private var lastValue: Long? = null

    override val linkRange = 64 // todo: make this configurable
    override val isLinked: Boolean
        get() = linkedTo != null

    override val dataPorts: Map<BlockFace, DataPort> = mapOf(
        BlockFace.EAST to DataPort.OUTPUT,
        BlockFace.WEST to DataPort.OUTPUT,
        BlockFace.NORTH to DataPort.OUTPUT,
        BlockFace.SOUTH to DataPort.OUTPUT,
    )

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity("status", label(block, 0.95))
        addEntity("filter", label(block, 0.825))
        addEntity("value", label(block, 0.7))

        refreshFilter()
        refreshStatus()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(linkKey, RebarSerializers.BLOCK_POSITION)?.let { linkedTo = it }
        pdc.get(filterKeyKey, ItemKeyType)?.let { filterKey = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        linkedTo?.let { pdc.set(linkKey, RebarSerializers.BLOCK_POSITION, it) }
        filterKey?.let { pdc.set(filterKeyKey, ItemKeyType, it) }
    }

    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # F # # # #")
            .addIngredient('F', FilterItem().also { filterItem = it })
            .addIngredient('#', GuiItems.background())
            .build()

    override fun onLinked(target: LinkTarget) {
        linkedTo = target.block.position

        refreshStatus()
        refreshValue()
    }

    override fun onUnlinked(target: LinkTarget) {
        linkedTo = null

        refreshStatus()
    }

    fun refreshValue() {
        val key = filterKey
        val receiver = linkedTo
            ?.takeIf { it.isChunkLoaded }
            ?.let { BlockStorage.getAs<Receiver>(it) }

        val value = if (key == null || receiver == null) null else receiver.count(key)
        if (value == lastValue) return

        lastValue = value

        getHeldEntity(TextDisplay::class.java, "value")
            ?.text(value?.let { UnitFormat.ITEMS.format(it).asComponent() })
    }

    private fun refreshFilter() {
        val text = filterKey?.let { tr("probe.filter.item", "item" to it.name) }
            ?: tr("probe.filter.none")

        getHeldEntity(TextDisplay::class.java, "filter")?.text(text)
    }

    private fun refreshStatus() {
        val text = if (linkedTo == null) {
            tr("probe.status.unlinked")
        } else {
            tr("probe.status.receiving")
        }

        getHeldEntity(TextDisplay::class.java, "status")?.text(text)
    }

    private fun getFilterStack(): ItemStack {
        val key = filterKey ?: return ItemStack.of(Material.BARRIER)
        val source = key.stack()

        return ItemStack.of(source.type).apply {
            setData(DataComponentTypes.ITEM_MODEL, source.getData(DataComponentTypes.ITEM_MODEL)!!)
        }
    }

    private fun setFilter(clickType: ClickType, player: Player) {
        val held = player.itemOnCursor

        if (clickType.isLeftClick && clickType.isShiftClick) {
            filterKey = null
            filterItem?.refresh()
        } else if (clickType.isLeftClick && !held.isEmpty) {
            filterKey = ItemKey.of(held)
            filterItem?.refresh()
        }

        refreshFilter()
        refreshValue()
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

    companion object : Listener {
        private val linkKey = splinterKey("linked_receiver")
        private val filterKeyKey = splinterKey("filter_key")

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun onLoad(e: RebarBlockLoadEvent) {
            (e.rebarBlock as? Probe)?.refreshValue()
        }

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun onBreak(e: RebarBlockBreakEvent) {
            val probe = e.rebarBlock as? Probe ?: return
            val target = probe.linkedTo?.takeIf { it.isChunkLoaded } ?: return

            BlockStorage.getAs<Receiver>(target)?.removeSource(probe)
        }
    }
}