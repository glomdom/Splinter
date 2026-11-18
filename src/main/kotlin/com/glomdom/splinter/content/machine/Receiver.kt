package com.glomdom.splinter.content.machine

import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.item.ItemKeyType
import com.glomdom.splinter.link.LinkSource
import com.glomdom.splinter.link.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer

class Receiver : RebarBlock, HologramHolder, LinkTarget {
    private val contributions = HashMap<BlockPosition, Object2LongOpenHashMap<ItemKey>>()
    private val consumers = mutableSetOf<BlockPosition>()

    private val aggregate = Object2LongOpenHashMap<ItemKey>()

    override val hologram = Hologram(this, Accent.RECEIVER)

    var total = 0L
        private set

    override val linkCapacity = 32
    override val sourceCount
        get() = contributions.size

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        hologram.spawn()

        refresh()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(contributionsKey, contributionsType)?.forEach { (pos, counts) ->
            val map = Object2LongOpenHashMap(counts)

            contributions[pos] = map
            addAll(map, 1)
        }

        pdc.get(consumersKey, positionsType)?.let { consumers += it }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(contributionsKey, contributionsType, contributions)
        pdc.set(consumersKey, positionsType, consumers)
    }

    override fun hasSource(source: LinkSource) =
        source.block.position.let { it in contributions || it in consumers }

    override fun addSource(source: LinkSource) {
        if (source is Reader) {
            contributions.putIfAbsent(source.block.position, Object2LongOpenHashMap())
            source.sync()
        } else {
            consumers += source.block.position
        }

        refresh()
    }

    override fun removeSource(source: LinkSource) {
        if (source is Reader) {
            contributions.remove(source.block.position)?.let {
                addAll(it, -1)
                notifyProbes()
            }
        } else {
            consumers -= source.block.position
        }

        refresh()
    }

    fun count(key: ItemKey): Long = aggregate.getLong(key)

    fun update(source: BlockPosition, counts: Object2LongOpenHashMap<ItemKey>) {
        val previous = contributions[source] ?: return
        if (previous == counts) return

        contributions[source] = counts

        addAll(previous, -1)
        addAll(counts, 1)

        refresh()
        notifyProbes()
    }

    fun take(key: ItemKey, amount: Int): ItemStack? {
        for ((pos, counts) in contributions) {
            if (counts.getLong(key) <= 0) continue
            if (!pos.isChunkLoaded) continue

            val taken = BlockStorage.getAs<Reader>(pos)?.extract(key, amount) ?: continue

            return taken
        }

        return null
    }

    override fun postLoad() = hologram.restore()

    fun refresh() = hologram.render {
        if (contributions.isEmpty()) status(Tone.WARN, "no_readers") else status(Tone.GOOD, "receiving")

        row("readers", Fmt.meter(sourceCount.toLong(), linkCapacity.toLong(), Accent.RECEIVER))
        row("consumers", consumers.size.takeIf { it > 0 }?.let { Fmt.number(it) })
        row("stored", Fmt.items(total, aggregate.size))

        val top = aggregate.object2LongEntrySet()
            .sortedByDescending { it.longValue }
            .take(TOP_ITEMS)

        if (top.isNotEmpty()) {
            separator()
            top.forEach { line(Fmt.stack(it.longValue, it.key)) }
        }
    }

    private fun addAll(counts: Object2LongOpenHashMap<ItemKey>, sign: Long) {
        Object2LongMaps.fastForEach(counts) { apply(it.key, it.longValue * sign) }
    }

    private fun apply(key: ItemKey, delta: Long) {
        if (delta == 0L) return

        if (aggregate.addTo(key, delta) + delta == 0L) {
            aggregate.removeLong(key)
        }

        total += delta
    }

    private fun notifyProbes() {
        for (pos in consumers) {
            if (!pos.isChunkLoaded) continue

            BlockStorage.getAs<Probe>(pos)?.refreshValue()
        }
    }

    companion object : Listener {
        private const val TOP_ITEMS = 5 // todo: maybe make this configurable

        private val contributionsKey = splinterKey("reader_contributions")
        private val contributionsType = RebarSerializers.MAP.mapTypeFrom(
            RebarSerializers.BLOCK_POSITION,
            RebarSerializers.MAP.mapTypeFrom(ItemKeyType, RebarSerializers.LONG),
        )

        private val consumersKey = splinterKey("receiver_consumers")
        private val positionsType = RebarSerializers.SET.setTypeFrom(RebarSerializers.BLOCK_POSITION)

        @Suppress("unused")
        @EventHandler(priority = EventPriority.MONITOR)
        private fun onLoad(e: RebarBlockLoadEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            receiver.refresh()

            for (pos in receiver.contributions.keys.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.sync()
            }
        }

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun onBreak(e: RebarBlockBreakEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return
            val sources = buildSet {
                addAll(receiver.contributions.keys)
                addAll(receiver.consumers)
            }

            for (pos in sources) {
                if (!pos.isChunkLoaded) continue

                (BlockStorage.get(pos) as? LinkSource)?.onUnlinked(receiver)
            }
        }
    }
}