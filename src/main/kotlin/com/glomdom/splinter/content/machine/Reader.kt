package com.glomdom.splinter.content.machine

import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.faceMarker
import com.glomdom.splinter.util.placementFacing
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.Rebar
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.util.IMMEDIATE_FACES
import io.github.pylonmc.rebar.util.delayTicks
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
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
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer

/**
 * Watches the container it faces and reports its item counts to the linked [Receiver].
 *
 * Inventory events mark readers dirty and they re-sync once per tick (see companion); the
 * [tick] only catches the faced block itself changing.
 */
class Reader : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, TickingRebarBlock, ReceiverLinked {
    override val link = ReceiverLink()

    private val labels = Labels(this)
    private var lastSubject: Material? = null
    private var total: Long? = null

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx)

        setTickInterval(10)

        addEntity("readerFace", faceMarker(FACE_MARKER, facing))
        labels.create("status", "subject", "value")

        refreshSubject()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        link.load(pdc)
    }

    override fun write(pdc: PersistentDataContainer) {
        link.save(pdc)
    }

    override fun tick() = refreshSubject()
    override fun onLinkChanged() = sync()

    /** Recounts the faced container and pushes the counts to the receiver. */
    fun sync() {
        val counts = Object2LongOpenHashMap<ItemKey>()
        var sum = 0L

        val container = block.getRelative(facing).getState(false) as? Container

        if (container != null) {
            for (stack in container.inventory) {
                if (stack == null || stack.isEmpty) continue

                counts.addTo(ItemKey.of(stack), stack.amount.toLong())
                sum += stack.amount
            }
        }

        total = sum.takeIf { container != null }
        render()

        link.receiver?.update(block.position, counts)
    }

    /** Removes up to [amount] of [key] from a single slot of the faced container. */
    fun extract(key: ItemKey, amount: Int): ItemStack? {
        val inv = (block.getRelative(facing).getState(false) as? Container)?.inventory ?: return null

        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot) ?: continue
            if (stack.isEmpty || ItemKey.of(stack) != key) continue

            val n = minOf(amount, stack.amount)
            val taken = stack.clone().apply { this.amount = n }

            inv.setItem(slot, if (stack.amount == n) null else stack.clone().apply { this.amount -= n })
            sync()

            return taken
        }

        return null
    }

    private fun refreshSubject() {
        val subject = block.getRelative(facing)
        if (subject.type == lastSubject) return

        lastSubject = subject.type

        sync()
    }

    private fun render() {
        val subject = block.getRelative(facing)

        labels["status"] = if (isLinked) tr("reader.status.transmitting") else tr("reader.status.unlinked")

        labels["subject"] = if (subject.getState(false) is Container) {
            tr("reader.subject.item", "subject" to Component.translatable(subject.type.translationKey()))
        } else {
            tr("reader.subject.invalid")
        }

        labels["value"] = total?.let { UnitFormat.ITEMS.format(it).asComponent() }
    }

    companion object : Listener {
        private val FACE_MARKER = Markers.marker(Material.BLUE_CONCRETE, "readerFace")

        private val dirty = mutableSetOf<BlockPosition>()
        private var flushScheduled = false

        /** Marks every reader facing [subject] for a re-sync on the next tick. */
        fun markSubject(subject: Block) {
            for (face in IMMEDIATE_FACES) {
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

                is BlockInventoryHolder -> {
                    markSubject(holder.block)
                }

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

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryClickEvent) {
            e.clickedInventory?.let(::markInventory)

            markInventory(e.view.topInventory)
        }

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryDragEvent) = markInventory(e.inventory)

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryMoveItemEvent) {
            markInventory(e.source)
            markInventory(e.destination)
        }

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun on(e: InventoryPickupItemEvent) = markInventory(e.inventory)

        @Suppress("unused")
        @EventHandler(priority = EventPriority.MONITOR)
        private fun on(e: InventoryCloseEvent) = markInventory(e.inventory)

        @Suppress("unused")
        @EventHandler(priority = EventPriority.MONITOR)
        private fun onLoad(e: RebarBlockLoadEvent) {
            (e.rebarBlock as? Reader)?.sync()
        }
    }
}