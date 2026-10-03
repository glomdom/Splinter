package com.glomdom.splinter.extensions

import com.glomdom.splinter.Splinter
import io.github.pylonmc.rebar.util.gui.unit.MetricPrefix
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor

val UnitFormat.Companion.READER: UnitFormat
    get() = UnitFormat(Splinter, "reader", defaultStyle = Style.style(TextColor.color(0xb2e01a)))

val UnitFormat.Companion.REDSTONE_STRENGTH: UnitFormat
    get() = UnitFormat(Splinter, "redstone_strength", defaultStyle = Style.style(NamedTextColor.RED))