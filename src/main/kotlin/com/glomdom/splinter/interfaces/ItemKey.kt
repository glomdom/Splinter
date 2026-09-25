package com.glomdom.splinter.interfaces

import io.github.pylonmc.rebar.item.RebarItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.EnumMap

sealed interface ItemKey {
    data class Rebar(val id: String) : ItemKey
    data class Vanilla(val type: Material) : ItemKey

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