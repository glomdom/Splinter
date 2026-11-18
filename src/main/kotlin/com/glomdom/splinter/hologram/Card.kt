package com.glomdom.splinter.hologram

import com.glomdom.splinter.item.ItemKey
import com.glomdom.splinter.util.tr
import io.github.pylonmc.rebar.util.position.BlockPosition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.Locale

enum class Tone(val color: TextColor) {
    GOOD(NamedTextColor.GREEN),
    ACTIVE(NamedTextColor.AQUA),
    WARN(NamedTextColor.YELLOW),
    BAD(NamedTextColor.RED),
    IDLE(NamedTextColor.GRAY),
}

/**
 * Builder for the body of a [Hologram]. Lines appear in call order under the header.
 * Row labels and statuses come from `splinter.hologram.label.*` or `splinter.hologram.status.*`.
 */
class Card internal constructor(title: Component, accent: TextColor) {
    private val lines = mutableListOf<Component>(
        Component.text()
            .append(rule(HEADER_DASHES))
            .append(Component.space())
            .append(title.color(accent).decorate(TextDecoration.BOLD))
            .append(Component.space())
            .append(rule(HEADER_DASHES))
            .build()
    )

    fun line(component: Component) {
        lines += component
    }

    fun status(tone: Tone, id: String) =
        line(Component.text("● ", tone.color).append(tr("hologram.status.$id").color(tone.color)))

    fun row(label: String, value: Component?) {
        if (value == null) return

        line(tr("hologram.row", "label" to tr("hologram.label.$label"), "value" to value))
    }

    fun separator() = line(rule(SEPARATOR_DASHES))

    internal fun build(): Component = Component.join(JoinConfiguration.newlines(), lines)

    private companion object {
        const val HEADER_DASHES = 6
        const val SEPARATOR_DASHES = 12

        fun rule(length: Int): Component =
            Component
                .text(" ".repeat(length), NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH)
    }
}

object Fmt {
    val NUMBER: TextColor = NamedTextColor.AQUA
    val MUTED: TextColor = NamedTextColor.DARK_GRAY

    private const val BAR_CHAR = "|"

    fun number(n: Long, color: TextColor = NUMBER): Component =
        Component.text(String.format(Locale.ROOT, "%,d", n), color)

    fun number(n: Int, color: TextColor = NUMBER) = number(n.toLong(), color)

    fun compact(n: Long, color: TextColor = NUMBER): Component {
        val abs = kotlin.math.abs(n)
        val text = when {
            abs < 1_000 -> n.toString()
            abs < 1_000_000 -> scaled(n, 1_000.0, "k")
            abs < 1_000_000_000 -> scaled(n, 1_000_000.0, "M")
            abs < 1_000_000_000_000 -> scaled(n, 1_000_000_000.0, "B")

            else -> scaled(n, 1_000_000_000_000.0, "T")
        }

        return Component.text(text, color)
    }

    private fun scaled(n: Long, unit: Double, suffix: String): String {
        val v = n / unit
        val digits = if (kotlin.math.abs(v) < 10) "%.2f" else if (kotlin.math.abs(v) < 100) "%.1f" else "%.0f"

        return String.format(Locale.ROOT, digits, v) + suffix
    }

    fun ratio(a: Component, b: Component): Component = tr("hologram.ratio", "a" to a, "b" to b)
    fun ratio(a: Long, b: Long) = ratio(number(a), number(b))

    fun bar(value: Long, max: Long, color: TextColor, width: Int = 20): Component {
        val fraction = if (max <= 0) 0.0 else (value.toDouble() / max).coerceIn(0.0, 1.0)

        var filled = (fraction * width).toInt()
        if (filled == 0 && value > 0 && max > 0) filled = 1

        return Component.text()
            .append(Component.text(BAR_CHAR.repeat(filled), color))
            .append(Component.text(BAR_CHAR.repeat(width - filled), MUTED))
            .build()
    }

    fun meter(value: Long, max: Long, color: TextColor, width: Int = 20): Component =
        Component.text()
            .append(bar(value, max, color, width))
            .append(Component.space())
            .append(ratio(value, max))
            .build()

    fun position(pos: BlockPosition): Component =
        Component.text("${pos.x}, ${pos.y}, ${pos.z}", NUMBER)

    fun items(total: Long, types: Int): Component =
        tr("hologram.items", "count" to compact(total), "types" to number(types))

    fun stack(count: Long, item: ItemKey): Component =
        tr("hologram.stack", "count" to compact(count), "item" to item.name)

    fun none(): Component = tr("hologram.none")
}
