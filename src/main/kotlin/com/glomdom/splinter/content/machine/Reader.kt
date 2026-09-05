package com.glomdom.splinter.content.machine

import com.glomdom.splinter.extensions.plus
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.Rebar
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.delayTicks
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import io.github.pylonmc.rebar.util.position.BlockPosition
import io.github.pylonmc.rebar.util.position.position
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
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem

class Reader : RebarBlock, DirectionalRebarBlock, EntityHolderRebarBlock, GuiRebarBlock, TickingRebarBlock, LinkSource {
    val readerFaceStack: ItemStackBuilder = ItemStackBuilder.of(Material.BLUE_CONCRETE)
        .addCustomModelDataString(key + ":readerFace")

    private var lastSubject: Material? = null
    private var lastValue: Int? = null
    private var configItem: ConfigItem? = null

    private var config: Config? = null
    private var lastRenderedConfig: Config? = null

    override val linkRange = 64
    override val isLinked get() = linkedTo != null

    private var linkedTo: BlockPosition? = null

    val value
        get() = config?.read(block.getRelative(facing))

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

        addEntity(
            "status", TextDisplayBuilder()
                .transformation(
                    TransformBuilder().translate(0.0, 0.95, 0.0).scale(0.6)
                )
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .text(
                    Component.translatable(
                        "splinter.reader.status.label",
                        RebarArgument.of("status", Component.translatable("splinter.reader.status.unconfigured"))
                    )
                )
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "subject", TextDisplayBuilder()
                .transformation(
                    TransformBuilder().translate(0.0, 0.825, 0.0).scale(0.6)
                )
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .text(
                    Component.translatable(
                        "splinter.reader.subject.label",
                        RebarArgument.of("subject", Component.translatable("splinter.reader.subject.invalid"))
                    )
                )
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "config", TextDisplayBuilder()
                .transformation(
                    TransformBuilder().translate(0.0, 0.7, 0.0).scale(0.6)
                )
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .text(
                    Component.translatable(
                        "splinter.reader.config.label",
                        RebarArgument.of("config", Component.translatable("splinter.reader.config.none"))
                    )
                )
                .build(block.location.toCenterLocation())
        )

        addEntity(
            "value", TextDisplayBuilder()
                .transformation(
                    TransformBuilder().translate(0.0, 0.575, 0.0).scale(0.6)
                )
                .billboard(Display.Billboard.VERTICAL)
                .backgroundColor(Color.fromARGB(0))
                .build(block.location.toCenterLocation())
        )

        refreshSubject()
        refreshValue()
        refreshConfig()
    }

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(configKey, configType)?.let({ config = it })
        pdc.get(linkKey, RebarSerializers.BLOCK_POSITION)?.let({ linkedTo = it })
    }

    override fun write(pdc: PersistentDataContainer) {
        config?.let { pdc.set(configKey, configType, it) }
        linkedTo?.let { pdc.set(linkKey, RebarSerializers.BLOCK_POSITION, it) }
    }

    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # C # # # #")
            .addIngredient('C', ConfigItem().also { configItem = it })
            .addIngredient('#', GuiItems.background())
            .build()

    override fun tick() = refreshSubject()

    override fun onLinked(target: LinkTarget) {
        linkedTo = target.block.position

        refreshStatus()
    }

    override fun onUnlinked(target: LinkTarget) {
        linkedTo = null

        refreshStatus()
    }

    private fun subjectComponent(type: Material): Component = when (type) {
        Material.CHEST -> Component.translatable("splinter.reader.subject.chest")

        else -> Component.translatable("splinter.reader.subject.invalid")
    }

    private fun refreshSubject() {
        val subject = block.getRelative(facing)
        if (subject.type == lastSubject) return

        lastSubject = subject.type

        getHeldEntity(TextDisplay::class.java, "subject")?.text(
            Component.translatable(
                "splinter.reader.subject.label",
                RebarArgument.of("subject", subjectComponent(subject.type))
            )
        )

        if (config?.appliesTo(subject) != true) {
            setConfig(defaultConfigFor(subject))
        }

        refreshValue()
    }

    internal fun refreshValue() {
        val count = config?.read(block.getRelative(facing))
        if (count == lastValue && config == lastRenderedConfig) return

        lastValue = count
        lastRenderedConfig = config

        linkedTo?.takeIf { it.isChunkLoaded }?.let { BlockStorage.getAs<Receiver>(it)?.refresh() }

        val display = getHeldEntity(TextDisplay::class.java, "value") ?: return
        display.text(
            count?.let { UnitFormat.ITEMS.format(it).asComponent() }
        )
    }

    private fun refreshStatus() {
        val status = when {
            config == null -> "splinter.reader.status.unconfigured"
            linkedTo == null -> "splinter.reader.status.unlinked"

            else -> "splinter.reader.status.transmitting"
        }

        getHeldEntity(TextDisplay::class.java, "status")?.text(
            Component.translatable(
                "splinter.reader.status.label",
                RebarArgument.of("status", Component.translatable(status))
            )
        )
    }

    private fun refreshConfig() {
        getHeldEntity(TextDisplay::class.java, "config")?.text(
            Component.translatable(
                "splinter.reader.config.label",
                RebarArgument.of(
                    "config", config?.label
                        ?: Component.translatable("splinter.reader.config.none")
                )
            )
        )

        refreshStatus()
    }

    private fun cycleConfig() {
        val options: List<Config?> = listOf(null) + Config.availableFor(block.getRelative(facing))
        if (options.size <= 1) return setConfig(null)

        setConfig(options[(options.indexOf(config) + 1) % options.size])
    }

    private fun setConfig(new: Config?) {
        if (new == config) return

        config = new

        refreshConfig()
        refreshValue()
        configItem?.refresh()
    }

    private fun defaultConfigFor(subject: Block): Config? =
        Config.availableFor(subject).firstOrNull()

    private inner class ConfigItem : AbstractItem() {
        fun refresh() = notifyWindows()

        override fun getItemProvider(viewer: Player) =
            ItemStackBuilder.gui(Material.COMPARATOR, "splinter:reader_config")
                .name(Component.translatable("splinter.reader.gui.config.name"))
                .lore(
                    Component.translatable(
                        "splinter.reader.gui.config.current",
                        RebarArgument.of(
                            "config", config?.label
                                ?: Component.translatable("splinter.reader.config.none")
                        )
                    ),
                    Component.translatable("splinter.reader.gui.config.hint"),
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) = cycleConfig()
    }

    companion object : Listener {
        private val configKey = splinterKey("reader_config")
        private val linkKey = splinterKey("linked_receiver")
        private val configType = RebarSerializers.ENUM.enumTypeFrom<Config>()

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
                positions.forEach { BlockStorage.getAs<Reader>(it)?.refreshValue() }
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
        private fun onBreak(e: RebarBlockBreakEvent) {
            val reader = e.rebarBlock as? Reader ?: return
            val target = reader.linkedTo?.takeIf { it.isChunkLoaded } ?: return

            BlockStorage.getAs<Receiver>(target)?.removeSource(reader)
        }
    }

    private enum class Config(val id: String) {
        COUNT("count") {
            override fun appliesTo(subject: Block) = subject.state is Container
            override fun read(subject: Block): Int? =
                (subject.state as? Container)?.inventory?.sumOf { it?.amount ?: 0 }
        };

        abstract fun appliesTo(subject: Block): Boolean
        abstract fun read(subject: Block): Int?

        val label: Component get() = Component.translatable("splinter.reader.config.$id")

        companion object {
            fun availableFor(subject: Block) = entries.filter { it.appliesTo(subject) }
        }
    }
}