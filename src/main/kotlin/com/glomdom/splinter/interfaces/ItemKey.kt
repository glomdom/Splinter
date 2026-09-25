package com.glomdom.splinter.interfaces

import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.registry.RebarRegistry
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import java.util.EnumMap

sealed interface ItemKey {
    val name: Component
        get() = stack().getData(DataComponentTypes.ITEM_NAME)
            ?: Component.translatable(stack().type.translationKey())

    fun stack(): ItemStack

    data class Rebar(val id: String) : ItemKey {
        override fun stack(): ItemStack {
            val schema = RebarRegistry.ITEMS[NamespacedKey.fromString(id)!!] ?: error("failed to get rebar item")

            return schema.getItemStack()
        }
    }

    data class Vanilla(val type: Material) : ItemKey {
        override fun stack(): ItemStack =
            ItemStack.of(type)
    }

    companion object {
        private val rebar = HashMap<String, Rebar>()
        private val vanilla = EnumMap<Material, Vanilla>(Material::class.java)

        fun of(stack: ItemStack): ItemKey {
            val rebarId = RebarItem.fromStack(stack)?.key?.toString()
            if (rebarId != null) {
                return rebar.getOrPut(rebarId) { Rebar(rebarId) }
            }

            return vanilla.getOrPut(stack.type) { Vanilla(stack.type) }
        }
    }
}