package com.glomdom.splinter.content.machine.data

import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.joml.Vector3d

object DataLine {
    val POOL = listOf(0.3495f, 0.3490f, 0.3485f)
    const val DIRECT = 0.3505f

    fun build(at: Location, from: Vector3d, to: Vector3d, thickness: Float): ItemDisplay =
        ItemDisplayBuilder()
            .transformation(
                LineBuilder()
                    .from(from)
                    .to(to)
                    .thickness(thickness)
                    .extraLength(thickness)
                    .build()
            )
            .itemStack(
                ItemStackBuilder.of(Material.RED_CONCRETE)
                    .addCustomModelDataString("${DataDisplays.PREFIX}:line")
            )
            .build(at)
}