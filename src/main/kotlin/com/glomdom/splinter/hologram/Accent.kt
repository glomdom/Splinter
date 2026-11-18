package com.glomdom.splinter.hologram

import net.kyori.adventure.text.format.TextColor

/** Title color of each block's hologram */
object Accent {
    val READER: TextColor = TextColor.color(0xF59BC0)
    val RECEIVER: TextColor = TextColor.color(0xD6DBE6)
    val PROBE: TextColor = TextColor.color(0xF06A62)
    val SPILLER: TextColor = TextColor.color(0xF7D85C)
    val CONSTANT: TextColor = TextColor.color(0x83E08F)
    val BOOLEAN_COMPARER: TextColor = TextColor.color(0xF7A94B)
    val ARITHMETIC: TextColor = TextColor.color(0xDD86EC)
    val REDSTONE_OUT: TextColor = TextColor.color(0xFF5555)

    // A - left B - right for two-input blocks
    val INPUT_A: TextColor = TextColor.color(0x6A8CFF)
    val INPUT_B: TextColor = TextColor.color(0xFF6A6A)
}
