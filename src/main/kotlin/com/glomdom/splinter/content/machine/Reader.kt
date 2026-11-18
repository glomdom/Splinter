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
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock
import io.github.pylonmc.rebar.block.interfaces.NoVanillaInventoryRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.logistics.LogisticGroupType
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot
import io.github.pylonmc.rebar.logistics.slot.VanillaInventoryLogisticSlot
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
    override fun onLinkChanged() = sync()

    override fun tick() {
        if (BlockStorage.get(block.getRelative(facing)) is LogisticRebarBlock) {
            sync()
        } else {
            refreshSubject()
        }
    }

    fun sync() {
        val counts = Object2LongOpenHashMap<ItemKey>()
        val slots = slots()

        snapshot = slots?.let {
            var sum = 0L
            var used = 0

            for (slot in it) {
                val stack = slot.getItemStack()
                if (stack == null || stack.isEmpty) continue

                val amount = slot.getAmount()

                counts.addTo(ItemKey.of(stack), amount)
                sum += amount
                used++
            }

            Snapshot(sum, counts.size, used, it.size)
        }

        render()

        link.receiver?.update(block.position, counts)
    }

    fun extract(key: ItemKey, amount: Int): ItemStack? {
        for (slot in slots() ?: return null) {
            val stack = slot.getItemStack()
            if (stack == null || stack.isEmpty || ItemKey.of(stack) != key) continue

            val has = slot.getAmount()
            val n = minOf(amount.toLong(), has)
            val rest = has - n

            if (rest == 0L) {
                if (!slot.canSet(null, 0)) continue
            } else {
                if (!slot.canSet(stack, rest)) continue
            }

            val taken = stack.asQuantity(n.toInt())

            if (rest == 0L) slot.set(null, 0) else slot.set(stack.clone(), rest)
            sync()

            return taken
        }

        return null
    }

    private fun slots(): List<LogisticSlot>? {
        val subject = block.getRelative(facing)

        when (val rebar = BlockStorage.get(subject)) {
            is LogisticRebarBlock -> return rebar.getLogisticGroups().values
                .filter { it.slotType != LogisticGroupType.INPUT }
                .flatMap { it.slots }
                .distinct()

            is NoVanillaInventoryRebarBlock -> return null
        }

        val inv = (subject.getState(false) as? Container)?.inventory ?: return null

        return (0 until inv.size).map { VanillaInventoryLogisticSlot(subject, inv, it) }
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