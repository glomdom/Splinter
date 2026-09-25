package com.glomdom.splinter.content.machine

import com.glomdom.splinter.interfaces.LinkSource
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.position.BlockPosition
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import xyz.xenondevs.invui.gui.Gui

class Probe : RebarBlock, EntityHolderRebarBlock, GuiRebarBlock, LinkSource {
    constructor(block: Block, ctx: BlockCreateContext) : super(block, ctx)
    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc)

    private var linkedTo: BlockPosition? = null

    override val linkRange = 64 // todo: make this configurable
    override val isLinked: Boolean
        get() = linkedTo != null

    override fun createGui(): Gui =
        Gui.builder()
            .setStructure("# # # # F # # # #")
            .addIngredient('F', getFilterStack())
            .addIngredient('#', GuiItems.background())
            .build()

    private fun getFilterStack(): ItemStack =
        ItemStack.of(Material.BARRIER)
}