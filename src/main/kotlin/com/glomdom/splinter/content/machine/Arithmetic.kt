package com.glomdom.splinter.content.machine

import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer

class Arithmetic : BinaryOperatorBlock<Arithmetic.Operator> {
    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx, OPERATORS)

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc, OPERATORS)

    override fun resultText(result: Long?): Component =
        if (result != null) Fmt.number(result, NamedTextColor.GREEN) else tr("hologram.undefined")

    override fun resultStatus(result: Long?) =
        if (result != null) Tone.GOOD to "computing" else Tone.BAD to "division_by_zero"

    enum class Operator(override val symbol: String, private val op: (Long, Long) -> Long) : BinaryOperator {
        ADDITION("+", Long::plus),
        SUBTRACTION("-", Long::minus),
        MULTIPLICATION("×", Long::times),
        INTEGER_DIVISION("÷", Long::div);

        override fun apply(a: Long, b: Long): Long? =
            try {
                op(a, b)
            } catch (_: ArithmeticException) {
                null
            }
    }

    companion object {
        private val OPERATORS = Operators(
            "arithmetic", splinterKey("arithmetic_operator"), Accent.ARITHMETIC, Operator::class.java,
        )
    }
}
