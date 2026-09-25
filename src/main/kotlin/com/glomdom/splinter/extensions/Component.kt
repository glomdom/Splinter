package com.glomdom.splinter.extensions

import net.kyori.adventure.text.Component

fun Component.withoutColor(): Component =
    color(null).children(children().map { it.withoutColor() })