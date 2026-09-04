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
import kotlin.reflect.KProperty

abstract class SplinterRegistry {
    enum class Kind { ITEM, BLOCK_ITEM, BLOCK, FLUID }

    val keys: Map<String, NamespacedKey> get() = keysByProperty

    private val pending = mutableListOf<() -> Unit>()
    private val keysByProperty = LinkedHashMap<String, NamespacedKey>()

    fun keyOf(property: KProperty<*>): NamespacedKey =
        keysByProperty[property.name] ?: error("${property.name} is not an entry of ${this::class.simpleName}")

    fun registerAll() {
        pending.forEach { it.invoke() }
        pending.clear()
    }

    protected fun <T : Any> entry(
        kind: Kind,
        register: (NamespacedKey, T) -> Unit = { _, _ -> },
        build: (NamespacedKey) -> T,
    ) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any, T>> { _, property ->
        val key = claim(property.name, kind)
        val value = build(key)
        pending += { register(key, value) }

        ReadOnlyProperty { _, _ -> value }
    }

    protected fun fluid(
        color: TextColor,
        material: Material,
        temperature: FluidTemperature = FluidTemperature.NORMAL,
    ) = entry(
        kind = Kind.FLUID,
        register = { _, fluid -> fluid.addTag(temperature); fluid.register() }
    ) { key ->
        RebarFluid(key, color, material)
    }

    protected inline fun <reified T : RebarItem> item(material: Material, page: SimpleStaticGuidePage) =
        item<T>(material, page, kind = Kind.ITEM)

    protected fun <T: RebarItem> item(material: Material, page: SimpleStaticGuidePage, look: Material) =
        entry(
            kind = Kind.ITEM,
            register = { key, stack ->
                RebarItem.register(RebarItem::class.java, stack, key)

                page.addItem(stack)
            }
        ) { key ->
            ItemStackBuilder.rebar(material, key).set(DataComponentTypes.ITEM_MODEL, look.key).build()
        }

    protected fun blockItem(material: Material, page: SimpleStaticGuidePage) =
        item<RebarItem>(material, page, kind = Kind.BLOCK_ITEM)

    protected fun <T : RebarBlock> block(material: Material, type: Class<T>) =
        entry(
            kind = Kind.BLOCK,
            register = { key, _ ->
                RebarBlock.register(key, material, type)
            }
        ) { key -> key }

    protected inline fun <reified T : RebarBlock> block(material: Material) =
        block(material, T::class.java)

    protected inline fun <reified T : RebarItem> item(material: Material, page: SimpleStaticGuidePage, kind: Kind) =
        entry(
            kind,
            register = { key, stack ->
                if (kind == Kind.BLOCK_ITEM) RebarItem.register(T::class.java, stack, key)
                else RebarItem.register(T::class.java, stack)

                page.addItem(stack)
            }
        ) { key ->
            ItemStackBuilder.rebar(material, key).build()
        }

    private fun claim(propertyName: String, kind: Kind): NamespacedKey {
        require(propertyName.all { it.isUpperCase() || it.isDigit() || it == '_' }) {
            "$propertyName must be SCREAMING_SNAKE_CASE to derive a key"
        }

        val key = splinterKey(propertyName.lowercase())
        require(claims.getOrPut(key) { mutableSetOf() }.add(kind)) { "$key is declared more than once" }

        keysByProperty[propertyName] = key

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