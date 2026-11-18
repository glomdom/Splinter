package com.glomdom.splinter.util

import io.github.pylonmc.rebar.i18n.RebarArgument
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike

/**
 * Translatable component for `splinter.<key>` in the `lang/` yml files.
 * Arguments are referenced in the lang file as `%name%`.
 */
fun tr(key: String, vararg args: Pair<String, ComponentLike>): Component =
    Component.translatable(
        "splinter.$key",
        *args.map { (name, value) -> RebarArgument.of(name, value) }.toTypedArray()
    )
