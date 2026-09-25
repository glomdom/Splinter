package com.glomdom.splinter.content.machine

import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.Rebar
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.delayTicks
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataContainer

class Reader : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, TickingRebarBlock, LinkSource {
    val readerFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.BLUE_CONCRETE)
        .addCustomModelDataString(key + ":readerFace")

    override val linkRange = 64
    override val isLinked get() = linkedTo != null

    private var linkedTo: BlockPosition? = null

    private var lastSubject: Material? = null
    private var lastTotal: Long? = null

    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = if (ctx.player?.isSneaking == true) {
            ctx.facing.oppositeFace
        } else {
            ctx.facing
        }

        setTickInterval(10)

        addEntity("readerFace", ItemDisplayBuilder().itemStack(readerFaceStack).transformation {
            it.lookAlong(facing.oppositeFace)
            it.translate(0.0, 0.0, -0.5)
            it.scale(0.25, 0.25, 0.1)
        }.build(block.location.toCenterLocation()))

        addEntity("status", label(0.95))
        addEntity("subject", label(0.825))
        addEntity("value", label(0.7))

        refreshStatus()
        refreshSubject()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(linkKey, RebarSerializers.BLOCK_POSITION)?.let { linkedTo = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        linkedTo?.let { pdc.set(linkKey, RebarSerializers.BLOCK_POSITION, it) }
    }

    override fun tick() = refreshSubject()

    override fun onLinked(target: LinkTarget) {
        linkedTo = target.block.position

        refreshStatus()
        sync()
    }

    override fun onUnlinked(target: LinkTarget) {
        linkedTo = null

        refreshStatus()
    }

    fun sync() {
        val counts = Object2LongOpenHashMap<ItemKey>()
        var total = 0L

        val container = block.getRelative(facing).getState(false) as? Container

        if (container != null) {
            for (stack in container.inventory) {
                if (stack == null || stack.isEmpty) continue

                counts.addTo(ItemKey.of(stack), stack.amount.toLong())
                total += stack.amount
            }
        }

        refreshValue(total.takeIf { container != null })

        val target = linkedTo?.takeIf { it.isChunkLoaded } ?: return
        BlockStorage.getAs<Receiver>(target)?.update(block.position, counts)
    }

    private fun refreshSubject() {
        val subject = block.getRelative(facing)
        if (subject.type == lastSubject) return

        lastSubject = subject.type

        val name = if (subject.getState(false) is Container) {
            Component.translatable(subject.type.translationKey())
        } else {
            Component.translatable("splinter.reader.subject.invalid")
        }

        getHeldEntity(TextDisplay::class.java, "subject")?.text(
            Component.translatable(
                "splinter.reader.subject.label",
                RebarArgument.of("subject", name)
            )
        )

        sync()
    }

    private fun refreshValue(total: Long?) {
        if (total == lastTotal) return

        lastTotal = total

        getHeldEntity(TextDisplay::class.java, "value")
            ?.text(total?.let { UnitFormat.ITEMS.format(it).asComponent() })
    }

    private fun refreshStatus() {
        val status = if (linkedTo == null) {
            "splinter.reader.status.unlinked"
        } else {
            "splinter.reader.status.transmitting"
        }

        getHeldEntity(TextDisplay::class.java, "status")?.text(
            Component.translatable(
                "splinter.reader.status.label",
                RebarArgument.of("status", Component.translatable(status))
            )
        )
    }

    private fun label(y: Double) =
        TextDisplayBuilder()
            .transformation(TransformBuilder().translate(0.0, y, 0.0).scale(0.6))
            .billboard(Display.Billboard.VERTICAL)
            .backgroundColor(Color.fromARGB(0))
            .build(block.location.toCenterLocation())

    companion object : Listener {
        private val linkKey = splinterKey("linked_receiver")

        private val faces = listOf(
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN,
        )

        private val dirty = mutableSetOf<BlockPosition>()
        private var flushScheduled = false

        fun markSubject(subject: Block) {
            for (face in faces) {
                val candidate = subject.getRelative(face)
                val reader = BlockStorage.get(candidate) as? Reader ?: continue
                if (reader.facing != face.oppositeFace) continue

                dirty += candidate.position
            }

            scheduleFlush()
        }

        private fun markInventory(inv: Inventory) {
            when (val holder = inv.holder) {
                is DoubleChest -> {
                    (holder.leftSide as? Chest)?.block?.let(::markSubject)
                    (holder.rightSide as? Chest)?.block?.let(::markSubject)
                }

                is BlockInventoryHolder -> markSubject(holder.block)
                else -> {}
            }
        }

        private fun scheduleFlush() {
            if (flushScheduled || dirty.isEmpty()) return
            flushScheduled = true

            Rebar.scope.launch(Rebar.mainThreadDispatcher) {
                delayTicks(1)
                flushScheduled = false

                val positions = dirty.toList()

                dirty.clear()
                positions.forEach { BlockStorage.getAs<Reader>(it)?.sync() }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryClickEvent) {
            e.clickedInventory?.let(::markInventory)

            markInventory(e.view.topInventory)
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryDragEvent) = markInventory(e.inventory)

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryMoveItemEvent) {
            markInventory(e.source)
            markInventory(e.destination)
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryPickupItemEvent) = markInventory(e.inventory)

        @EventHandler(priority = EventPriority.MONITOR)
        private fun on(e: InventoryCloseEvent) = markInventory(e.inventory)

        @EventHandler
        private fun onLoad(e: RebarBlockLoadEvent) {
            (e.rebarBlock as? Reader)?.sync()
        }

        @EventHandler
        private fun onBreak(e: RebarBlockBreakEvent) {
            val reader = e.rebarBlock as? Reader ?: return
            val target = reader.linkedTo?.takeIf { it.isChunkLoaded } ?: return

            BlockStorage.getAs<Receiver>(target)?.removeSource(reader)
        }
    }
}