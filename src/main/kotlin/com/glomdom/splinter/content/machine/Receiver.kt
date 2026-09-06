package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.extensions.READER
import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem

class Receiver : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, LinkTarget, DataEndpoint {
    private val sources = mutableSetOf<BlockPosition>()

    private var configItem: ConfigItem? = null
    private var combine: Combine = Combine.SUM

    override val linkCapacity = 4
    override val sourceCount
        get() = sources.size

    override val dataPorts = mapOf(
        BlockFace.EAST to DataPort.OUTPUT,
        BlockFace.WEST to DataPort.OUTPUT,
        BlockFace.NORTH to DataPort.OUTPUT,
        BlockFace.SOUTH to DataPort.OUTPUT,
    )

    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity(
            "status", TextDisplayBuilder()
                .transformation(TransformBuilder().translate(0.0, 0.95, 0.0).scale(0.6))
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "readers", TextDisplayBuilder()
                .transformation(TransformBuilder().translate(0.0, 0.825, 0.0).scale(0.6))
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "config", TextDisplayBuilder()
                .transformation(TransformBuilder().translate(0.0, 0.7, 0.0).scale(0.6))
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "value", TextDisplayBuilder()
                .transformation(TransformBuilder().translate(0.0, 0.575, 0.0).scale(0.6))
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .build(block.location.toCenterLocation())
        )

        refresh()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(sourcesKey, sourcesType)?.let { sources += it }
        combine = pdc.get(combineKey, combineType) ?: Combine.SUM
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(sourcesKey, sourcesType, sources)
        pdc.set(combineKey, combineType, combine)
    }

    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # C # # # #")
            .addIngredient('C', ConfigItem().also { configItem = it })
            .addIngredient('#', GuiItems.background())
            .build()

    override fun hasSource(source: LinkSource) =
        source.block.position in sources

    override fun addSource(source: LinkSource) {
        sources += source.block.position

        refreshReaders()
        refreshValue()
    }

    override fun removeSource(source: LinkSource) {
        sources -= source.block.position

        refreshReaders()
        refreshValue()
    }

    fun refresh() {
        refreshReaders()
        refreshConfig()
        refreshValue()
    }

    private fun refreshReaders() {
        getHeldEntity(TextDisplay::class.java, "readers")
            ?.text(UnitFormat.READER.format(sourceCount).asComponent())
    }

    private fun refreshConfig() {
        getHeldEntity(TextDisplay::class.java, "config")?.text(
            Component.translatable(
                "splinter.receiver.config.label",
                RebarArgument.of("config", combine.label)
            )
        )
    }

    private fun refreshValue() {
        val valueEntity = getHeldEntity(TextDisplay::class.java, "value") ?: return
        val statusEntity = getHeldEntity(TextDisplay::class.java, "status")

        val status = when (val result = fold()) {
            is FoldResult.Value -> {
                valueEntity.text(Component.text(result.value))
                "splinter.receiver.status.receiving"
            }

            FoldResult.Empty -> {
                valueEntity.text(null)
                "splinter.receiver.status.unlinked"
            }

            FoldResult.Error -> "splinter.receiver.status.error"
        }

        statusEntity?.text(
            Component.translatable(
                "splinter.receiver.status.label",
                RebarArgument.of("status", Component.translatable(status))
            )
        )
    }

    private fun cycleConfig() =
        setConfig(Combine.entries[(Combine.entries.indexOf(combine) + 1) % Combine.entries.size])

    private fun setConfig(new: Combine) {
        if (new == combine) return

        combine = new

        refreshConfig()
        refreshValue()
        configItem?.refresh()
    }

    private inner class ConfigItem : AbstractItem() {
        fun refresh() = notifyWindows()

        override fun getItemProvider(viewer: Player) =
            ItemStackBuilder.gui(Material.COMPARATOR, "splinter:receiver_config")
                .name(Component.translatable("splinter.receiver.gui.config.name"))
                .lore(
                    Component.translatable(
                        "splinter.receiver.gui.config.current",
                        RebarArgument.of("config", combine.label)
                    ),
                    Component.translatable("splinter.receiver.gui.config.hint"),
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) = cycleConfig()
    }

    private fun fold(): FoldResult {
        val values = mutableListOf<Int>()

        for (source in sources) {
            if (!source.isChunkLoaded) continue

            val reader = BlockStorage.getAs<Reader>(source) ?: return FoldResult.Error
            val value = reader.value ?: return FoldResult.Error

            values += value
        }

        return if (values.isEmpty()) FoldResult.Empty else FoldResult.Value(combine.reduce(values))
    }

    sealed interface FoldResult {
        data class Value(val value: Int) : FoldResult
        data object Empty : FoldResult
        data object Error : FoldResult
    }

    enum class Combine(val id: String) {
        SUM("sum") { override fun reduce(values: List<Int>) = values.sum() },
        MIN("min") { override fun reduce(values: List<Int>) = values.min() },
        MAX("max") { override fun reduce(values: List<Int>) = values.max() };

        abstract fun reduce(values: List<Int>): Int

        val label: Component get() = Component.translatable("splinter.receiver.config.$id")
    }

    companion object : Listener {
        private val sourcesKey = splinterKey("linked_readers")
        private val sourcesType = RebarSerializers.SET.setTypeFrom(RebarSerializers.BLOCK_POSITION)

        private val combineKey = splinterKey("receiver_combine")
        private val combineType = RebarSerializers.ENUM.enumTypeFrom(Combine::class.java)

        @EventHandler
        private fun onLoad(e: RebarBlockLoadEvent) {
            (e.rebarBlock as? Receiver)?.refresh()
        }

        @EventHandler
        private fun onBreak(e: RebarBlockBreakEvent) {
            val receiver = e.rebarBlock as? Receiver ?: return

            for (pos in receiver.sources.toList()) {
                if (!pos.isChunkLoaded) continue

                BlockStorage.getAs<Reader>(pos)?.onUnlinked(receiver)
            }
        }
    }
}