package com.glomdom.splinter.datatypes

import com.glomdom.splinter.interfaces.ItemKey
import org.bukkit.Material
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType

object ItemKeyType : PersistentDataType<String, ItemKey> {
    override fun getPrimitiveType() = String::class.java
    override fun getComplexType() = ItemKey::class.java

    override fun toPrimitive(complex: ItemKey, context: PersistentDataAdapterContext): String =
        when (complex) {
            is ItemKey.Rebar -> "rebar:${complex.id}"
            is ItemKey.Vanilla -> "vanilla:${complex.type.key}"
        }

    override fun fromPrimitive(primitive: String, context: PersistentDataAdapterContext): ItemKey {
        val (kind, rest) = primitive.split(':', limit = 2)

        return when (kind) {
            "rebar" -> {
                ItemKey.Rebar(rest)
            }

            "vanilla" -> {
                ItemKey.Vanilla(Material.matchMaterial(rest) ?: error("Unknown material '$rest'"))
            }

            else -> {
                error("Unknown item key kind '$kind'")
            }
        }
    }

}