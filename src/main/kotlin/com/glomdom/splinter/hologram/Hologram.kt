package com.glomdom.splinter.hologram

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

/** A block that shows a [Hologram] card above itself. */
interface HologramHolder : EntityHolderRebarBlock {
    val hologram: Hologram<*>
}

/**
 * A single text-display "card" floating above a block: colored title then a divider, then whatever
 * the block puts in [render].
 *
 * The last rendered text is kept and applied by [HologramListener] once the display entity loads.
 */
class Hologram<T>(private val owner: T, private val accent: TextColor) where T : RebarBlock, T : HologramHolder {
    private var desired: Component? = null
    private var shown: Component? = null

    val entityId get() = owner.getHeldEntityUuid(ENTITY)

    fun spawn() {
        val display = TextDisplayBuilder()
            .transformation(TransformBuilder().translate(0.0, OFFSET_Y, 0.0).scale(SCALE))
            .billboard(Display.Billboard.VERTICAL)
            .backgroundColor(BACKGROUND)
            .build(owner.block.location.toCenterLocation())
            .apply {
                isShadowed = true
                lineWidth = LINE_WIDTH
            }

        owner.addEntity(ENTITY, display)
        shown = null
    }

    fun restore() {
        if (ENTITY !in owner.heldEntities) {
            spawn()
        }

        flush()
    }

    fun render(build: Card.() -> Unit) {
        desired = Card(owner.nameTranslationKey, accent).apply(build).build()

        flush()
    }

    fun flush(force: Boolean = false) {
        val text = desired ?: return
        if (!force && text == shown) return

        val display = owner.getHeldEntity(TextDisplay::class.java, ENTITY) ?: return

        display.text(text)
        shown = text
    }

    companion object {
        const val ENTITY = "hologram"

        private const val OFFSET_Y = 0.6
        private const val SCALE = 0.45f
        private const val LINE_WIDTH = 240
        private val BACKGROUND = Color.fromARGB(0xB0, 0x10, 0x10, 0x16)
    }
}
