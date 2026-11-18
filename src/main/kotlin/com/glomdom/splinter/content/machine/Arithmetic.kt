package com.glomdom.splinter.content.machine

import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer

/** Outputs `A <op> B` for basic integer arithmetic. */
class Arithmetic : BinaryOperatorBlock<Arithmetic.Operator> {
    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx, OPERATORS)

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc, OPERATORS)

    override fun outputLabel(result: Long?): Component =
        if (result != null) tr("arithmetic.output", "value" to Component.text(result)) else tr("arithmetic.error")

    enum class Operator(override val symbol: String, private val op: (Long, Long) -> Long) : BinaryOperator {
        ADDITION("+", Long::plus),
        SUBTRACTION("-", Long::minus),
        MULTIPLICATION("*", Long::times),
        INTEGER_DIVISION("//", Long::div);

        override fun apply(a: Long, b: Long): Long? =
            try {
                op(a, b)
            } catch (_: ArithmeticException) {
                null
            }
    }

    companion object {
        private val OPERATORS = Operators("arithmetic", splinterKey("arithmetic_operator"), Operator::class.java)
    }
}
