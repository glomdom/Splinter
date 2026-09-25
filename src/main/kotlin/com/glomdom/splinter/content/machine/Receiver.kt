package com.glomdom.splinter.content.machine

import com.glomdom.splinter.datatypes.ItemKeyType
import com.glomdom.splinter.extensions.READER
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
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
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class Receiver : RebarBlock, EntityHolderRebarBlock, LinkTarget {
    private val contributions = HashMap<BlockPosition, Object2LongOpenHashMap<ItemKey>>()
    private var probes = mutableSetOf<BlockPosition>()
    private val aggregate = Object2LongOpenHashMap<ItemKey>()

    var total = 0L
        private set

    override val linkCapacity = 4
    override val sourceCount
        get() = contributions.size

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity("status", label(block, 0.95))
        addEntity("readers", label(block, 0.825))
        addEntity("value", label(block, 0.7))

        refresh()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(contributionsKey, contributionsType)?.forEach { (pos, counts) ->
            val map = Object2LongOpenHashMap(counts)

            contributions[pos] = map
            addAll(map, 1)
        }

        pdc.get(probesKey, probesType)?.let { probes = it.toMutableSet() }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(contributionsKey, contributionsType, contributions)
        pdc.set(probesKey, probesType, probes)
    }

    override fun hasSource(source: LinkSource) =
        source.block.position.let { it in contributions || it in probes }

    override fun addSource(source: LinkSource) {
        when (source) {
            is Reader -> {
                contributions.putIfAbsent(source.block.position, Object2LongOpenHashMap())
                source.sync()
            }

            is Probe -> {
                probes += source.block.position
            }
        }

        refresh()
    }

    override fun removeSource(source: LinkSource) {
        when (source) {
            is Reader -> {
                contributions.remove(source.block.position)?.let {
                    addAll(it, -1)
                    notifyProbes()
                }
            }

            is Probe -> {
                probes -= source.block.position
            }
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

        refreshValue()
        notifyProbes()
    }

    fun refresh() {
        refreshReaders()
        refreshStatus()
        refreshValue()
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

    private fun refreshReaders() {
        getHeldEntity(TextDisplay::class.java, "readers")
            ?.text(UnitFormat.READER.format(sourceCount).asComponent())
    }

    private fun refreshStatus() {
        val text = if (contributions.isEmpty()) {
            tr("receiver.status.unlinked")
        } else {
            tr("receiver.status.receiving")
        }

        getHeldEntity(TextDisplay::class.java, "status")?.text(text)
    }

    private fun refreshValue() {
        getHeldEntity(TextDisplay::class.java, "value")
            ?.text(UnitFormat.ITEMS.format(total).asComponent())
    }

    private fun notifyProbes() {
        for (pos in probes) {
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

        private val probesKey = splinterKey("receiver_probes")
        private val probesType = RebarSerializers.SET.setTypeFrom(RebarSerializers.BLOCK_POSITION)

        @Suppress("unused")
        @EventHandler
        private fun onLoad(e: RebarBlockLoadEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            receiver.refresh()

            for (pos in receiver.contributions.keys.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.sync()
            }
        }

        @Suppress("unused")
        @EventHandler
        private fun onBreak(e: RebarBlockBreakEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            for (pos in receiver.contributions.keys.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.onUnlinked(receiver)
            }

            for (pos in receiver.probes.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Probe>(pos)?.onUnlinked(receiver)
            }
        }
    }
}