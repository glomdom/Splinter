package com.glomdom.splinter.content.machine

import com.glomdom.splinter.item.ItemKeyType
import com.glomdom.splinter.util.READER
import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.link.LinkSource
import com.glomdom.splinter.link.LinkTarget
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
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

/**
 * Hub of a logistics network. Aggregates the item counts reported by linked [Reader]s, answers
 * [Probe] queries and serves extraction requests from [Spiller]s.
 *
 * Only readers count towards [linkCapacity]; probes and spillers are unlimited by design.
 */
class Receiver : RebarBlock, EntityHolderRebarBlock, LinkTarget {
    /** Linked readers and the counts each last reported. */
    private val contributions = HashMap<BlockPosition, Object2LongOpenHashMap<ItemKey>>()

    /** Every other linked source (probes, spillers): they use the network but don't add to it. */
    private val consumers = mutableSetOf<BlockPosition>()

    private val aggregate = Object2LongOpenHashMap<ItemKey>()

    private val labels = Labels(this)

    var total = 0L
        private set

    override val linkCapacity = 32
    override val sourceCount
        get() = contributions.size

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        labels.create("status", "readers", "value")

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

        // receivers saved before probes and spillers were merged into `consumers`
        for (key in legacyConsumerKeys) {
            pdc.get(key, positionsType)?.let { consumers += it }
        }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(contributionsKey, contributionsType, contributions)
        pdc.set(consumersKey, positionsType, consumers)

        legacyConsumerKeys.forEach(pdc::remove)
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

    fun refresh() {
        labels["status"] = if (contributions.isEmpty()) tr("receiver.status.unlinked") else tr("receiver.status.receiving")
        labels["readers"] = UnitFormat.READER.format(sourceCount).asComponent()
        labels["value"] = UnitFormat.ITEMS.format(total).asComponent()
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
        private val contributionsKey = splinterKey("reader_contributions")
        private val contributionsType = RebarSerializers.MAP.mapTypeFrom(
            RebarSerializers.BLOCK_POSITION,
            RebarSerializers.MAP.mapTypeFrom(ItemKeyType, RebarSerializers.LONG),
        )

        private val consumersKey = splinterKey("receiver_consumers")
        private val positionsType = RebarSerializers.SET.setTypeFrom(RebarSerializers.BLOCK_POSITION)

        private val legacyConsumerKeys = listOf(splinterKey("receiver_probes"), splinterKey("receiver_spillers"))

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