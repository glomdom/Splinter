package com.glomdom.splinter.content.machine

import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer

/** Outputs `1` when `A <op> B` holds, otherwise `0`. */
class BooleanComparer : BinaryOperatorBlock<BooleanComparer.Operator> {
    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx, OPERATORS)

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc, OPERATORS)

    override fun outputLabel(result: Long?): Component =
        if (result == 1L) tr("boolean_comparer.output.high") else tr("boolean_comparer.output.low")

    enum class Operator(override val symbol: String, private val test: (Long, Long) -> Boolean) : BinaryOperator {
        GREATER(">", { a, b -> a > b }),
        GREATER_EQUAL(">=", { a, b -> a >= b }),
        LESS("<", { a, b -> a < b }),
        LESS_EQUAL("<=", { a, b -> a <= b }),
        EQUAL("==", { a, b -> a == b }),
        NOT_EQUAL("!=", { a, b -> a != b });

        override fun apply(a: Long, b: Long): Long = if (test(a, b)) 1 else 0
    }

    companion object {
        private val OPERATORS = Operators("boolean_comparer", splinterKey("comparer_operator"), Operator::class.java)
    }
}
