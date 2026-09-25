package com.glomdom.splinter.content.machine

import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.utilities.label
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.position.BlockPosition
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.ItemProvider

class Probe : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, LinkSource {
    private var linkedTo: BlockPosition? = null
    private var filterKey: ItemKey? = null
    private var filterItem: FilterItem? = null

    override val linkRange = 64 // todo: make this configurable
    override val isLinked: Boolean
        get() = linkedTo != null

    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity("filter", label(block, 0.95))
        addEntity("value", label(block, 0.825))

        refreshFilter()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # F # # # #")
            .addIngredient('F', FilterItem().also { filterItem = it })
            .addIngredient('#', GuiItems.background())
            .build()

    private fun refreshFilter() {
        val stackName = if (filterKey == null) {
            Component.translatable("splinter.probe.filter.none")
        } else {
            Component.translatable(
                "splinter.probe.filter.item",
                RebarArgument.of("item", filterKey!!.name)
            )
        }

        getHeldEntity(TextDisplay::class.java, "filter")
            ?.text(
                Component.translatable(
                    "splinter.probe.filter.label",
                    RebarArgument.of("filter", stackName)
                )
            )
    }

    private fun getFilterStack(): ItemStack {
        if (filterKey == null) {
            return ItemStack.of(Material.BARRIER)
        }

        return filterKey!!.stack()
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
    }

    private inner class FilterItem : AbstractItem() {
        fun refresh() = notifyWindows()

        override fun getItemProvider(viewer: Player) =
            ItemStackBuilder.gui(getFilterStack(), "splinter:filter_item")
                .name(Component.translatable("splinter.gui.filter.name"))
                .lore(
                    Component.translatable(
                        "splinter.gui.filter.current", RebarArgument.of(
                            "current",
                            filterKey?.let {
                                Component.translatable(
                                    "splinter.gui.filter.item",
                                    RebarArgument.of("item", it.name)
                                )
                            } ?: Component.translatable("splinter.gui.filter.none")
                        )
                    ),
                    Component.translatable("splinter.gui.filter.hint")
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) =
            setFilter(clickType, player)
    }
}