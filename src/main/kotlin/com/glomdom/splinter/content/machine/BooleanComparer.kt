package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.extensions.left
import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.extensions.right
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataContainer

class BooleanComparer : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, DataEndpoint {
    val inputAFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.BLUE_CONCRETE)
        .addCustomModelDataString(key + ":inputAFace")

    val inputBFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.RED_CONCRETE)
        .addCustomModelDataString(key + ":inputBFace")

    val outputFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.ORANGE_CONCRETE)
        .addCustomModelDataString(key + ":outputFace")

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

    private var oldA = 0L
    private var oldB = 0L

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = if (ctx.player?.isSneaking == true) {
            ctx.facing.oppositeFace
        } else {
            ctx.facing
        }

        addEntity("inputAFace", ItemDisplayBuilder().itemStack(inputAFaceStack).transformation {
            it.lookAlong(facing.right())
            it.translate(0.0, 0.0, -0.5)
            it.scale(0.25, 0.25, 0.1)
        }.build(block.location.toCenterLocation()))

        addEntity("inputBFace", ItemDisplayBuilder().itemStack(inputBFaceStack).transformation {
            it.lookAlong(facing.left())
            it.translate(0.0, 0.0, -0.5)
            it.scale(0.25, 0.25, 0.1)
        }.build(block.location.toCenterLocation()))

        addEntity("outputFace", ItemDisplayBuilder().itemStack(outputFaceStack).transformation {
            it.lookAlong(facing.oppositeFace)
            it.translate(0.0, 0.0, -0.5)
            it.scale(0.25, 0.25, 0.1)
        }.build(block.location.toCenterLocation()))

        addEntity("operation", label(block, 1.075))
        addEntity("aValue", label(block, 0.95))
        addEntity("bValue", label(block, 0.825))
        addEntity("outputValue", label(block, 0.7))

        refreshValues()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    override fun onInput(port: DataPort) {
        if (port == inputA) {
            if (port.value == oldA) return

            oldA = port.value
        } else if (port == inputB) {
            if (port.value == oldB) return

            oldB = port.value
        }

        compareValues()
    }

    private fun refreshValues() {
        getHeldEntity(TextDisplay::class.java, "operation")?.text(Component.text("A > B"))
        getHeldEntity(TextDisplay::class.java, "aValue")?.text(tr("boolean_comparer.value.a", "value" to Component.text(oldA)))
        getHeldEntity(TextDisplay::class.java, "bValue")?.text(tr("boolean_comparer.value.b", "value" to Component.text(oldB)))
        getHeldEntity(TextDisplay::class.java, "outputValue")?.text(tr("boolean_comparer.value.output", "value" to Component.text(outputPort.value)))
    }

    private fun compareValues() {
        if (inputA.value > inputB.value) {
            outputPort.emit(1)
        } else {
            outputPort.emit(0)
        }

        refreshValues()
    }
}