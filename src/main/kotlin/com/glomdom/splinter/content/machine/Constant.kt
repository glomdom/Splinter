package com.glomdom.splinter.content.machine

import com.glomdom.splinter.data.DataEndpoint
import com.glomdom.splinter.data.DataPort
import com.glomdom.splinter.hologram.Accent
import com.glomdom.splinter.hologram.Fmt
import com.glomdom.splinter.hologram.Hologram
import com.glomdom.splinter.hologram.HologramHolder
import com.glomdom.splinter.hologram.Tone
import com.glomdom.splinter.splinterKey
import com.glomdom.splinter.util.addPortMarkers
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.datatypes.RebarSerializers
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
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

class Constant : RebarBlock, HologramHolder, InteractRebarBlockHandler, DataEndpoint {
    private var constant = 0L

    override val dataPorts: Map<BlockFace, DataPort> =
        BlockFace.entries
            .filter { it.isCartesian && it != BlockFace.UP }
            .associateWith { DataPort(this, it, DataPort.Kind.OUTPUT) }

    override val hologram = Hologram(this, Accent.CONSTANT)

    @Suppress("unused")
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx) {
        hologram.spawn()
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

    override fun postLoad() {
        hologram.restore()
        refreshConstant()
    }

    override fun onWiringChanged() = render()

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

        render()
    }

    private fun render() = hologram.render {
        if (connectedPorts > 0) status(Tone.GOOD, "outputting") else status(Tone.IDLE, "not_wired")

        row("value", Fmt.number(constant))
        row("wired", Fmt.ratio(connectedPorts.toLong(), dataPorts.size.toLong()))
        line(tr("hologram.hint.edit"))
    }

    companion object {
        private val constantKey = splinterKey("constant")
    }
}
