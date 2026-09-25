package com.glomdom.splinter.content.machine

import com.glomdom.splinter.content.machine.data.DataEndpoint
import com.glomdom.splinter.content.machine.data.DataPort
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.utilities.label
import com.glomdom.splinter.utilities.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent
import io.github.pylonmc.rebar.i18n.RebarTranslator
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataContainer

class Constant : RebarBlock, EntityHolderRebarBlock, InteractRebarBlockHandler, DataEndpoint {
    private var constant = 0L

    override val dataPorts: Map<BlockFace, DataPort> =
        BlockFace.entries
            .filter { it.isCartesian }
            .associateWith { DataPort(this, it, DataPort.Kind.OUTPUT) }

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        addEntity("constant", label(block, 0.95))

        refreshConstant()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(constantKey, RebarSerializers.LONG)?.let { constant = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(constantKey, RebarSerializers.LONG, constant)
    }

    override fun onInteractedWith(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.player.isSneaking) return

        val locale = event.player.locale()

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(GlobalTranslator.render(tr("dialog.constant.title"), locale))
                        .inputs(
                            listOf(
                                DialogInput.text("value", GlobalTranslator.render(tr("dialog.constant.label"), locale))
                                    .initial(constant.toString())
                                    .maxLength(20)
                                    .build()
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.builder(GlobalTranslator.render(tr("dialog.confirm"), locale))
                            .action(
                                DialogAction.customClick(
                                    { response, _ ->
                                        if (BlockStorage.get(block) !== this) return@customClick

                                        val parsed = response.getText("value")?.trim()?.toLongOrNull()
                                        if (parsed == null) {
                                            event.player.sendMessage(tr("messages.constant.invalid"))
                                        } else {
                                            setConstant(parsed)
                                            event.player.sendMessage(tr("messages.constant.ok"))
                                        }
                                    },
                                    ClickCallback.Options.builder().uses(1).build()
                                )
                            )
                            .build(),
                        ActionButton.builder(GlobalTranslator.render(tr("dialog.cancel"), locale)).build()
                    )
                )
        }

        event.player.showDialog(dialog)
    }

    private fun setConstant(new: Long) {
        if (constant == new) return
        constant = new

        refreshConstant()
    }

    private fun refreshConstant() {
        dataPorts.values.forEach { it.emit(constant) }

        getHeldEntity(TextDisplay::class.java, "constant")?.text(
            tr(
                "constant.value",
                "value" to Component.text(constant)
            )
        )
    }

    companion object : Listener {
        private val constantKey = splinterKey("constant")

        @Suppress("unused")
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        private fun onLoad(e: RebarBlockLoadEvent) {
            (e.rebarBlock as? Constant)?.refreshConstant()
        }
    }
}