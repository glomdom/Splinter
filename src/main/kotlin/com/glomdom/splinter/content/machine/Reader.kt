package com.glomdom.splinter.content.machine

import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.link.ReceiverLink
import com.glomdom.splinter.link.ReceiverLinked
import com.glomdom.splinter.util.Markers
import com.glomdom.splinter.util.faceMarker
import com.glomdom.splinter.util.placementFacing
import io.github.pylonmc.rebar.Rebar
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.util.IMMEDIATE_FACES
import io.github.pylonmc.rebar.util.delayTicks
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

class Reader : RebarBlock, DirectionalRebarBlock, HologramHolder, TickingRebarBlock, ReceiverLinked {
    override val link = ReceiverLink()
    override val hologram = Hologram(this, Accent.READER)

    private class Snapshot(val total: Long, val types: Int, val usedSlots: Int, val slots: Int)

    private var lastSubject: Material? = null
    private var snapshot: Snapshot? = null

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        facing = placementFacing(ctx)

        setTickInterval(10)

        addEntity("readerFace", faceMarker(FACE_MARKER, facing))
        hologram.spawn()

        refreshSubject()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        link.load(pdc)
    }

    override fun write(pdc: PersistentDataContainer) {
        link.save(pdc)
    }

    override fun postLoad() = hologram.restore()
    override fun tick() = refreshSubject()
    override fun onLinkChanged() = sync()

    /** Recounts the faced container and pushes the counts to the receiver. */
    fun sync() {
        val counts = Object2LongOpenHashMap<ItemKey>()
        val container = block.getRelative(facing).getState(false) as? Container

        snapshot = container?.inventory?.let { inv ->
            var sum = 0L
            var used = 0

            for (stack in inv) {
                if (stack == null || stack.isEmpty) continue

                counts.addTo(ItemKey.of(stack), stack.amount.toLong())
                sum += stack.amount
                used++
            }

            Snapshot(sum, counts.size, used, inv.size)
        }

        render()

        link.receiver?.update(block.position, counts)
    }

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

    private fun render() = hologram.render {
        val snapshot = snapshot

        when {
            !isLinked -> status(Tone.WARN, "unlinked")
            snapshot == null -> status(Tone.BAD, "no_inventory")
            else -> status(Tone.GOOD, "transmitting")
        }

        row(
            "reading",
            if (snapshot != null) {
                Component.translatable(block.getRelative(facing).type.translationKey(), Fmt.NUMBER)
            } else {
                Fmt.none()
            }
        )

        if (snapshot != null) {
            row("contents", Fmt.items(snapshot.total, snapshot.types))
            row("slots", Fmt.meter(snapshot.usedSlots.toLong(), snapshot.slots.toLong(), Accent.READER))
        }

        row("receiver", link.target?.let(Fmt::position))
    }

    companion object : Listener {
        private val FACE_MARKER = Markers.marker(Material.BLUE_CONCRETE, "readerFace")

        private val dirty = mutableSetOf<BlockPosition>()
        private var flushScheduled = false

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