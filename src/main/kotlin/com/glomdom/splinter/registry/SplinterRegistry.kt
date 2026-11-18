package com.glomdom.splinter.registry

import com.glomdom.splinter.Splinter
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.fluid.RebarFluid
import io.github.pylonmc.rebar.fluid.tags.FluidTemperature
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Declarative registry. Each `val FOO by item(...)` / `block(...)` / ... derives the key `splinter:foo`
 * from the property name and queues registration until [registerAll] is called.
 *
 * A block and its item share a key, so `SplinterBlocks.READER` and `SplinterItems.READER` must have the
 * same name; [validateClaims] fails startup if a block item has no matching block.
 */
abstract class SplinterRegistry {
    enum class Kind { ITEM, BLOCK_ITEM, BLOCK, FLUID }

    private val pending = mutableListOf<() -> Unit>()

    fun registerAll() {
        pending.forEach { it() }
        pending.clear()
    }

    /** A standalone item backed by the [RebarItem] subclass [T]. [look] overrides the item model. */
    protected inline fun <reified T : RebarItem> item(material: Material, page: SimpleStaticGuidePage, look: Material? = null) =
        itemEntry(T::class.java, Kind.ITEM, material, page, look)

    /** The item form of a block declared in [SplinterBlocks] under the same name. */
    protected fun blockItem(material: Material, page: SimpleStaticGuidePage, look: Material? = null) =
        itemEntry(RebarItem::class.java, Kind.BLOCK_ITEM, material, page, look)

    protected inline fun <reified T : RebarBlock> block(material: Material) =
        entry(Kind.BLOCK, register = { key, _ -> RebarBlock.register(key, material, T::class.java) }) { it }

    protected fun fluid(
        color: TextColor,
        material: Material,
        temperature: FluidTemperature = FluidTemperature.NORMAL,
    ) = entry(
        Kind.FLUID,
        register = { _, fluid -> fluid.addTag(temperature); fluid.register() }
    ) { key ->
        RebarFluid(key, color, material)
    }

    @PublishedApi
    internal fun <T : RebarItem> itemEntry(
        type: Class<T>,
        kind: Kind,
        material: Material,
        page: SimpleStaticGuidePage,
        look: Material?,
    ) = entry(
        kind,
        register = { key, stack ->
            if (kind == Kind.BLOCK_ITEM) RebarItem.register(type, stack, key) else RebarItem.register(type, stack)
            page.addItem(stack)
        }
    ) { key ->
        ItemStackBuilder.rebar(material, key)
            .apply { if (look != null) set(DataComponentTypes.ITEM_MODEL, look.key) }
            .build()
    }

    /** Claims a key from the property name, builds the value now and defers [register] to [registerAll]. */
    @PublishedApi
    internal fun <T : Any> entry(
        kind: Kind,
        register: (NamespacedKey, T) -> Unit,
        build: (NamespacedKey) -> T,
    ) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any, T>> { _, property ->
        val key = claim(property.name, kind)
        val value = build(key)
        pending += { register(key, value) }

        ReadOnlyProperty { _, _ -> value }
    }

    private fun claim(propertyName: String, kind: Kind): NamespacedKey {
        require(propertyName.all { it.isUpperCase() || it.isDigit() || it == '_' }) {
            "$propertyName must be SCREAMING_SNAKE_CASE to derive a key"
        }

        val key = splinterKey(propertyName.lowercase())
        require(claims.getOrPut(key) { mutableSetOf() }.add(kind)) { "$key is declared more than once" }

        return key
    }

    companion object {
        private val claims = mutableMapOf<NamespacedKey, MutableSet<Kind>>()

        fun validateClaims() {
            val dangling = claims.filterValues { Kind.BLOCK_ITEM in it && Kind.BLOCK !in it }.keys

            check(dangling.isEmpty()) { "Block items with no matching block: ${dangling.joinToString()}" }

            Splinter.logger.info("Registry claims have been validated successfully")
        }
    }
}
