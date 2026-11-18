package com.glomdom.splinter.util

import com.glomdom.splinter.Splinter
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor

// Lang entries live under `unit.<name>` in the lang files.

private val reader by lazy { UnitFormat(Splinter, "reader", defaultStyle = Style.style(TextColor.color(0xb2e01a))) }
private val redstoneStrength by lazy { UnitFormat(Splinter, "redstone_strength", defaultStyle = Style.style(NamedTextColor.RED)) }

val UnitFormat.Companion.READER: UnitFormat
    get() = reader

val UnitFormat.Companion.REDSTONE_STRENGTH: UnitFormat
    get() = redstoneStrength
