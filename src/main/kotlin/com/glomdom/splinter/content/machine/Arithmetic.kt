package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.extensions.addPortMarkers
import com.glomdom.splinter.extensions.left
import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.extensions.right
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.Labels
import com.glomdom.splinter.utilities.placementFacing
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataContainer

class Arithmetic : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, InteractRebarBlockHandler, DataEndpoint {
    override val dataPorts by lazy {
        val a = facing.left()
        val b = facing.right()

        mapOf(
            facing to DataPort(this, facing, DataPort.Kind.OUTPUT),
            a to DataPort(this, a, DataPort.Kind.INPUT),
            b to DataPort(this, b, DataPort.Kind.INPUT),
        )
    }

    private val inputA get() = dataPorts.getValue(facing.left())
    private val inputB get() = dataPorts.getValue(facing.right())
    private val outputPort get() = dataPorts.getValue(facing)

    private val labels = Labels(this)
    private var operator = Operator.ADDITION

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx, horizontal = true)

        addPortMarkers(
            mapOf(
                facing.left() to inputAFaceStack,
                facing.right() to inputBFaceStack,
            )
        )

        labels.create("operation", "values", "output")

        evaluate()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(operatorKey, operatorType)?.let { operator = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(operatorKey, operatorType, operator)
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
        operator = Operator.entries[(operator.ordinal + 1) % Operator.entries.size]

        block.world.playSound(block.location.toCenterLocation(), Sound.BLOCK_COMPARATOR_CLICK, 0.5f, 1.2f)
        evaluate()
    }

    private fun evaluate() {
        try {
            val result = operator.action(inputA.value, inputB.value)

            outputPort.emit(result)
            render(result)
        } catch (_: ArithmeticException) {
            outputPort.emit(0)
            render(null)
        }
    }

    private fun render(result: Long?) {
        val op = Component.text(operator.symbol)

        labels["operation"] = tr("arithmetic.operation", "op" to op)

        labels["values"] = tr(
            "arithmetic.values",
            "a" to Component.text(inputA.value),
            "op" to op,
            "b" to Component.text(inputB.value),
        )

        if (result != null) {
            labels["output"] = tr("arithmetic.output", "value" to Component.text(result))
        } else {
            labels["output"] = tr("arithmetic.error")
        }
    }


    private enum class Operator(val symbol: String, val action: (Long, Long) -> Long) {
        ADDITION("+", { a, b -> a + b }),
        SUBTRACTION("-", { a, b -> a - b }),
        MULTIPLICATION("*", { a, b -> a * b }),
        INTEGER_DIVISION("//", { a, b -> a / b }),
    }

    companion object {
        private val operatorKey = splinterKey("arithmetic_operator")
        private val operatorType = RebarSerializers.ENUM.enumTypeFrom(Operator::class.java)

        private val inputAFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.BLUE_CONCRETE)
            .addCustomModelDataString("splinter:inputAFace")

        private val inputBFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.RED_CONCRETE)
            .addCustomModelDataString("splinter:inputBFace")
    }
}