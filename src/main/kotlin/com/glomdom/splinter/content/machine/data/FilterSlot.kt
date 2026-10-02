package com.glomdom.splinter.content.machine.data

import com.glomdom.splinter.datatypes.ItemKeyType
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem

class FilterSlot(private val onChange: () -> Unit) {
    var key: ItemKey? = null
        private set

    private var item: SlotItem? = null

    fun load(pdc: PersistentDataContainer) {
        pdc.get(pdcKey, ItemKeyType)?.let { key = it }
    }

    fun save(pdc: PersistentDataContainer) {
        key?.let { pdc.set(pdcKey, ItemKeyType, it) }
    }

    fun gui(): Gui =
        Gui.builder()
            .setStructure("# # # # F # # # #")
            .addIngredient('F', SlotItem().also { item = it })
            .addIngredient('#', GuiItems.background())
            .build()

    fun label(): Component =
        key?.let { tr("filter.item", "item" to it.name) } ?: tr("filter.none")

    private fun set(new: ItemKey?) {
        if (new == key) return

        key = new

        item?.refresh()
        onChange()
    }

    private fun displayStack(): ItemStack {
        val key = key ?: return ItemStack.of(Material.BARRIER)
        val source = key.stack()

        return ItemStack.of(source.type).apply {
            setData(DataComponentTypes.ITEM_MODEL, source.getData(DataComponentTypes.ITEM_MODEL)!!)
        }
    }

    private inner class SlotItem : AbstractItem() {
        fun refresh() = notifyWindows()

        override fun getItemProvider(viewer: Player) =
            ItemStackBuilder.gui(displayStack(), "splinter:filter_item")
                .name(tr("gui.filter.name"))
                .lore(
                    key?.let { tr("gui.filter.current.item", "name" to it.name) }
                        ?: tr("gui.filter.current.none"),
                    tr("gui.filter.hint")
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (!clickType.isLeftClick) return

            if (clickType.isShiftClick) {
                set(null)
                return
            }

            val held = player.itemOnCursor
            if (!held.isEmpty) set(ItemKey.of(held))
        }
    }

    companion object {
        private val pdcKey = splinterKey("filter_key")
    }
}