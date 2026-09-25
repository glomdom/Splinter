package com.glomdom.splinter.content.machine

import com.glomdom.splinter.datatypes.ItemKeyType
import com.glomdom.splinter.extensions.READER
import com.glomdom.splinter.interfaces.ItemKey
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataContainer

class Receiver : RebarBlock, EntityHolderRebarBlock, LinkTarget {
    private val contributions = HashMap<BlockPosition, Object2LongOpenHashMap<ItemKey>>()
    private val aggregate = Object2LongOpenHashMap<ItemKey>()

    var total = 0L
        private set

    override val linkCapacity = 4
    override val sourceCount
        get() = contributions.size

    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity("status", label(0.95))
        addEntity("readers", label(0.825))
        addEntity("value", label(0.7))

        refresh()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(contributionsKey, contributionsType)?.forEach { (pos, counts) ->
            val map = Object2LongOpenHashMap(counts)

            contributions[pos] = map
            addAll(map, 1)
        }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(contributionsKey, contributionsType, contributions)
    }

    override fun hasSource(source: LinkSource) =
        source.block.position in contributions

    override fun addSource(source: LinkSource) {
        contributions.putIfAbsent(source.block.position, Object2LongOpenHashMap())

        refresh()
        (source as? Reader)?.sync()
    }

    override fun removeSource(source: LinkSource) {
        contributions.remove(source.block.position)?.let { addAll(it, -1) }

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

    fun refresh() {
        refreshReaders()
        refreshStatus()
        refreshValue()
    }

    private fun refreshReaders() {
        getHeldEntity(TextDisplay::class.java, "readers")
            ?.text(UnitFormat.READER.format(sourceCount).asComponent())
    }

    private fun refreshStatus() {
        val status = if (contributions.isEmpty()) {
            "splinter.receiver.status.unlinked"
        } else {
            "splinter.receiver.status.receiving"
        }

        getHeldEntity(TextDisplay::class.java, "status")?.text(
            Component.translatable(
                "splinter.receiver.status.label",
                RebarArgument.of("status", Component.translatable(status))
            )
        )
    }

    private fun refreshValue() {
        getHeldEntity(TextDisplay::class.java, "value")
            ?.text(UnitFormat.ITEMS.format(total).asComponent())
    }

    private fun label(y: Double) =
        TextDisplayBuilder()
            .transformation(TransformBuilder().translate(0.0, y, 0.0).scale(0.6))
            .billboard(Display.Billboard.VERTICAL)
            .backgroundColor(Color.fromARGB(0))
            .build(block.location.toCenterLocation())

    companion object : Listener {
        private val contributionsKey = splinterKey("reader_contributions")
        private val contributionsType = RebarSerializers.MAP.mapTypeFrom(
            RebarSerializers.BLOCK_POSITION,
            RebarSerializers.MAP.mapTypeFrom(ItemKeyType, RebarSerializers.LONG),
        )

        @EventHandler
        private fun onLoad(e: RebarBlockLoadEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            receiver.refresh()

            for (pos in receiver.contributions.keys.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.sync()
            }
        }

        @EventHandler
        private fun onBreak(e: RebarBlockBreakEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            for (pos in receiver.contributions.keys.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.onUnlinked(receiver)
            }
        }
    }
}