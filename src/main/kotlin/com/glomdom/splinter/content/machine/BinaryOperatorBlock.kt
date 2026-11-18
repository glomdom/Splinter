package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.left
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.right
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
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
 * Base for horizontal blocks that combine two inputs into one output.
 *
 * Subclasses must declare their operator enum (via [Operators]) and how to show the result.
 * Language keys that will be used: `<langKey>.operator.<operator name in lowercase>`.
 */
abstract class BinaryOperatorBlock<Op> : RebarBlock, DirectionalRebarBlock, HologramHolder,
    InteractRebarBlockHandler, DataEndpoint where Op : Enum<Op>, Op : BinaryOperator {

    class Operators<Op>(
        val langKey: String,
        val pdcKey: NamespacedKey,
        val accent: TextColor,
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

    private val operators: Operators<Op>
    final override val hologram: Hologram<BinaryOperatorBlock<Op>>

    protected var operator: Op
        private set

    constructor(block: Block, ctx: BlockCreateContext, operators: Operators<Op>) : super(block, ctx) {
        this.operators = operators
        this.operator = operators.all.first()
        this.hologram = Hologram(this, operators.accent)

        facing = placementFacing(ctx, horizontal = true)

        addPortMarkers(mapOf(facing.left() to Markers.INPUT_A, facing.right() to Markers.INPUT_B))
        hologram.spawn()

        evaluate()
    }

    constructor(block: Block, pdc: PersistentDataContainer, operators: Operators<Op>) : super(block, pdc) {
        this.operators = operators
        this.operator = pdc.get(operators.pdcKey, operators.pdcType) ?: operators.all.first()
        this.hologram = Hologram(this, operators.accent)
    }

    protected abstract fun resultText(result: Long?): Component
    protected abstract fun resultStatus(result: Long?): Pair<Tone, String>

    protected open val resultArrow: String
        get() = "="

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(operators.pdcKey, operators.pdcType, operator)
    }

    override fun postLoad() {
        hologram.restore()
        evaluate()
    }

    override fun onInput(port: DataPort) = evaluate()
    override fun onWiringChanged() = evaluate()

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

    private fun render(result: Long?) = hologram.render {
        val missing = listOf(inputA, inputB).count { !it.isConnected }
        val (tone, status) = if (missing > 0) Tone.WARN to "missing_input" else resultStatus(result)

        status(tone, status)

        line(
            Component.text()
                .append(Fmt.number(inputA.value, Accent.INPUT_A))
                .append(Component.text(" ${operator.symbol} ", NamedTextColor.GOLD))
                .append(Fmt.number(inputB.value, Accent.INPUT_B))
                .append(Component.text(" $resultArrow ", Fmt.MUTED))
                .append(resultText(result))
                .build()
        )

        row(
            "mode",
            Component.text()
                .append(tr("${operators.langKey}.operator.${operator.name.lowercase()}").color(NamedTextColor.GOLD))
                .append(Component.text(" (${operator.ordinal + 1}/${operators.all.size})", Fmt.MUTED))
                .build()
        )

        row("wired", Fmt.ratio(connectedPorts.toLong(), dataPorts.size.toLong()))
        line(tr("hologram.hint.cycle"))
    }
}
