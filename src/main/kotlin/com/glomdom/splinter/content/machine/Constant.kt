package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.Labels
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
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
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataContainer

/** Outputs a fixed number on every side. Shift + right-click opens a dialog to change it. */
class Constant : RebarBlock, EntityHolderRebarBlock, InteractRebarBlockHandler, DataEndpoint {
    private var constant = 0L

    override val dataPorts: Map<BlockFace, DataPort> =
        BlockFace.entries
            .filter { it.isCartesian }
            .associateWith { DataPort(this, it, DataPort.Kind.OUTPUT) }

    private val labels = Labels(this)

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        labels.create("constant")
        addPortMarkers()

        refreshConstant()
    }

    @Suppress("unused")
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        pdc.get(constantKey, RebarSerializers.LONG)?.let { constant = it }
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(constantKey, RebarSerializers.LONG, constant)
    }

    override fun postLoad() = refreshConstant()

    override fun onInteractedWith(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.player.isSneaking) return

        event.player.showDialog(editDialog(event.player))
    }

    private fun editDialog(player: Player): Dialog {
        val locale = player.locale()
        fun render(key: String) = GlobalTranslator.render(tr(key), locale)

        val input = DialogInput.text("value", render("dialog.constant.label"))
            .initial(constant.toString())
            .maxLength(20)
            .build()

        val confirm = ActionButton.builder(render("dialog.confirm"))
            .action(
                DialogAction.customClick(
                    { response, _ ->
                        // the block may have been broken while the dialog was open
                        if (BlockStorage.get(block) !== this) return@customClick

                        val parsed = response.getText("value")?.trim()?.toLongOrNull()
                        if (parsed == null) {
                            player.sendMessage(tr("messages.constant.invalid"))
                        } else {
                            setConstant(parsed)
                            player.sendMessage(tr("messages.constant.ok"))
                        }
                    },
                    ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val cancel = ActionButton.builder(render("dialog.cancel")).build()

        return Dialog.create { builder ->
            builder.empty()
                .base(DialogBase.builder(render("dialog.constant.title")).inputs(listOf(input)).build())
                .type(DialogType.confirmation(confirm, cancel))
        }
    }

    private fun setConstant(new: Long) {
        if (constant == new) return

        constant = new

        refreshConstant()
    }

    private fun refreshConstant() {
        dataPorts.values.forEach { it.emit(constant) }

        labels["constant"] = tr("constant.value", "value" to Component.text(constant))
    }

    companion object {
        private val constantKey = splinterKey("constant")
    }
}
