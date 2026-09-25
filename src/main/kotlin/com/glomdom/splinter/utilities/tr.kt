package com.glomdom.splinter.utilities

import io.github.pylonmc.rebar.i18n.RebarArgument
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike

fun tr(key: String, vararg args: Pair<String, ComponentLike>): Component =
    Component.translatable(
        "splinter.$key",
        *args.map { (name, value) -> RebarArgument.of(name, value) }.toTypedArray()
    )