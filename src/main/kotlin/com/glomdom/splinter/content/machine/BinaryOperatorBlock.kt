package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.left
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.right
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataContainer

/** An operation a [BinaryOperatorBlock] can perform. `null` means the result is undefined (e.g. division by zero). */
interface BinaryOperator {
    val symbol: String

    fun apply(a: Long, b: Long): Long?
}

/**
 * Base for horizontal blocks that combine two inputs into one output:
 *
 * - input **A** on the left (blue marker), input **B** on the right (red marker)
 * - output in front, emitting `operator(A, B)` (or `0` if undefined)
 * - shift + right-click with an empty hand cycles through the operators
 *
 * Subclasses only declare their operator enum (via [Operators]) and how to render the result.
 * Lang keys used: `<langKey>.operation`, `<langKey>.values`, plus whatever [outputLabel] returns.
 */
abstract class BinaryOperatorBlock<Op> : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock,
    InteractRebarBlockHandler, DataEndpoint where Op : Enum<Op>, Op : BinaryOperator {

    /** Static description of a subclass's operator set. Keep one instance in the subclass's companion. */
    class Operators<Op>(
        val langKey: String,
        val pdcKey: NamespacedKey,
        type: Class<Op>,
    ) where Op : Enum<Op>, Op : BinaryOperator {
        val all: List<Op> = type.enumConstants.toList()
        val pdcType = RebarSerializers.ENUM.enumTypeFrom(type)
    }

    final override val dataPorts by lazy {
        mapOf(
            facing to DataPort(this, facing, DataPort.Kind.OUTPUT),
            facing.left() to DataPort(this, facing.left(), DataPort.Kind.INPUT),
            facing.right() to DataPort(this, facing.right(), DataPort.Kind.INPUT),
        )
    }

    private val inputA get() = dataPorts.getValue(facing.left())
    private val inputB get() = dataPorts.getValue(facing.right())
    private val output get() = dataPorts.getValue(facing)

    private val labels = Labels(this)
    private val operators: Operators<Op>

    protected var operator: Op
        private set

    constructor(block: Block, ctx: BlockCreateContext, operators: Operators<Op>) : super(block, ctx) {
        this.operators = operators
        this.operator = operators.all.first()

        facing = placementFacing(ctx, horizontal = true)

        addPortMarkers(mapOf(facing.left() to Markers.INPUT_A, facing.right() to Markers.INPUT_B))
        labels.create("operation", "values", "output")

        evaluate()
    }

    constructor(block: Block, pdc: PersistentDataContainer, operators: Operators<Op>) : super(block, pdc) {
        this.operators = operators
        this.operator = pdc.get(operators.pdcKey, operators.pdcType) ?: operators.all.first()
    }

    /** Label shown on the bottom line. [result] is `null` when the operator's result is undefined. */
    protected abstract fun outputLabel(result: Long?): Component

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(operators.pdcKey, operators.pdcType, operator)
    }

    override fun postLoad() = evaluate()
    override fun onInput(port: DataPort) = evaluate()

    override fun onInteractedWith(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.player.isSneaking) return
        if (!event.player.inventory.itemInMainHand.isEmpty) return

        event.isCancelled = true
        cycleOperator()
    }

    private fun cycleOperator() {
        operator = operators.all[(operator.ordinal + 1) % operators.all.size]

        block.world.playSound(block.location.toCenterLocation(), Sound.BLOCK_COMPARATOR_CLICK, 0.5f, 1.2f)
        evaluate()
    }

    private fun evaluate() {
        val result = operator.apply(inputA.value, inputB.value)

        output.emit(result ?: 0)
        render(result)
    }

    private fun render(result: Long?) {
        val op = Component.text(operator.symbol)

        labels["operation"] = tr("${operators.langKey}.operation", "op" to op)
        labels["values"] = tr(
            "${operators.langKey}.values",
            "a" to Component.text(inputA.value),
            "op" to op,
            "b" to Component.text(inputB.value),
        )
        labels["output"] = outputLabel(result)
    }
}
