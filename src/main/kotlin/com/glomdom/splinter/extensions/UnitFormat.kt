package com.glomdom.splinter.extensions

import io.github.pylonmc.rebar.util.gui.unit.MetricPrefix
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor

private fun splinterUnit(
    name: String,
    color: TextColor,
    abbreviate: Boolean = true,
    prefix: MetricPrefix = MetricPrefix.NONE,
) = UnitFormat(
    name = name,
    singular = Component.translatable("splinter.unit.$name.singular"),
    plural = Component.translatable("splinter.unit.$name.plural"),
    abbreviation = Component.translatable("splinter.unit.$name.abbr").takeIf { abbreviate },
    defaultPrefix = prefix,
    defaultStyle = Style.style(color),
)

val UnitFormat.Companion.READER: UnitFormat
    get() = splinterUnit("reader", TextColor.color(0xb2e01a), abbreviate = false)