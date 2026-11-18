package com.glomdom.splinter.util

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

/**
 * A vertical stack of floating text lines above a block.
 *
 * Call [create] once when the block is first placed (the displays are persisted as held entities),
 * then assign lines with `labels["name"] = component`. Unchanged values are skipped.
 */
class Labels<T>(private val owner: T) where T : RebarBlock, T : EntityHolderRebarBlock {
    private val shown = HashMap<String, Component?>()

    fun create(vararg names: String) {
        names.forEachIndexed { i, name ->
            owner.addEntity(name, spawnLine(TOP - i * STEP))
        }
    }

    operator fun set(name: String, text: Component?) {
        if (shown.containsKey(name) && shown[name] == text) return

        val entity = owner.getHeldEntity(TextDisplay::class.java, name) ?: return

        entity.text(text)
        shown[name] = text
    }

    private fun spawnLine(y: Double) =
        TextDisplayBuilder()
            .transformation(TransformBuilder().translate(0.0, y, 0.0).scale(0.6))
            .billboard(Display.Billboard.VERTICAL)
            .backgroundColor(Color.fromARGB(0))
            .build(owner.block.location.toCenterLocation())

    companion object {
        private const val TOP = 0.95
        private const val STEP = 0.125
    }
}
