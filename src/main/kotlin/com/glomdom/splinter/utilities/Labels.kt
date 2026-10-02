package com.glomdom.splinter.utilities

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import net.kyori.adventure.text.Component
import org.bukkit.entity.TextDisplay

/**
 * Stack of cached labels on a block
 */
class Labels<T>(private val owner: T) where T : RebarBlock, T : EntityHolderRebarBlock {
    private val shown = HashMap<String, Component?>()

    fun create(vararg names: String) {
        names.forEachIndexed { i, name ->
            owner.addEntity(name, label(owner.block, TOP - i * STEP))
        }
    }

    operator fun set(name: String, text: Component?) {
        if (shown.containsKey(name) && shown[name] == text) return

        val entity = owner.getHeldEntity(TextDisplay::class.java, name) ?: return

        entity.text(text)
        shown[name] = text
    }

    companion object {
        private const val TOP = 0.95
        private const val STEP = 0.125
    }
}