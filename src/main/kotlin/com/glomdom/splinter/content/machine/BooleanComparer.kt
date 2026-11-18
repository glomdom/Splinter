package com.glomdom.splinter.content.machine

import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer

class BooleanComparer : BinaryOperatorBlock<BooleanComparer.Operator> {
    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx, OPERATORS)

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc, OPERATORS)

    override fun resultText(result: Long?): Component =
        if (result == 1L) tr("hologram.bool.true") else tr("hologram.bool.false")

    override fun resultStatus(result: Long?) =
        if (result == 1L) Tone.GOOD to "high" else Tone.IDLE to "low"

    override val resultArrow: String
        get() = "»"

    enum class Operator(override val symbol: String, private val test: (Long, Long) -> Boolean) : BinaryOperator {
        GREATER(">", { a, b -> a > b }),
        GREATER_EQUAL("≥", { a, b -> a >= b }),
        LESS("<", { a, b -> a < b }),
        LESS_EQUAL("≤", { a, b -> a <= b }),
        EQUAL("=", { a, b -> a == b }),
        NOT_EQUAL("≠", { a, b -> a != b });

        override fun apply(a: Long, b: Long): Long = if (test(a, b)) 1 else 0
    }

    companion object {
        private val OPERATORS = Operators(
            "boolean_comparer", splinterKey("comparer_operator"), Accent.BOOLEAN_COMPARER, Operator::class.java,
        )
    }
}
