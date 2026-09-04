package com.glomdom.splinter.interfaces

import io.github.pylonmc.rebar.i18n.RebarArgument
import net.kyori.adventure.text.Component

sealed interface LinkResult {
    val messageKey: String
    val arguments: List<RebarArgument>
        get() = emptyList()

    val succeeded: Boolean
        get() = this is Linked || this is Unlinked

    data class Linked(val source: LinkSource, val target: LinkTarget) : LinkResult {
        override val messageKey = "splinter.messages.linker.linked"
        override val arguments
            get() = listOf(
                RebarArgument.of("source", source.displayName),
                RebarArgument.of("target", target.displayName),
                RebarArgument.of("x", target.block.x),
                RebarArgument.of("y", target.block.y),
                RebarArgument.of("z", target.block.z)
            )
    }

    data class Unlinked(val source: LinkSource, val target: LinkTarget) : LinkResult {
        override val messageKey = "splinter.messages.linker.unlinked"
        override val arguments
            get() = listOf(
                RebarArgument.of("source", source.displayName),
                RebarArgument.of("target", target.displayName)
            )
    }

    data class AlreadyLinked(val source: LinkSource, val target: LinkTarget) : LinkResult {
        override val messageKey = "splinter.messages.linker.already_linked"
        override val arguments
            get() = listOf(RebarArgument.of("source", source.displayName))
    }

    data class CapacityFull(val target: LinkTarget, val limit: Int) : LinkResult {
        override val messageKey = "splinter.messages.linker.capacity_full"
        override val arguments
            get() = listOf(
                RebarArgument.of("target", target.displayName),
                RebarArgument.of("limit", limit)
            )
    }

    data class TooFar(val distance: Int, val maxRange: Int) : LinkResult {
        override val messageKey = "splinter.messages.linker.too_far"
        override val arguments
            get() = listOf(
                RebarArgument.of("distance", distance),
                RebarArgument.of("range", maxRange)
            )
    }

    data object IncompatiblePair : LinkResult {
        override val messageKey = "splinter.messages.linker.incompatible"
    }

    data object SameBlock : LinkResult {
        override val messageKey = "splinter.messages.linker.same_block"
    }

    data object DifferentWorld : LinkResult {
        override val messageKey = "splinter.messages.linker.different_world"
    }

    data class SourceBusy(val source: LinkSource) : LinkResult {
        override val messageKey = "splinter.messages.linker.source_busy"
        override val arguments get() = listOf(RebarArgument.of("source", source.displayName))
    }
}

fun LinkResult.toComponent(): Component =
    Component.translatable(messageKey, arguments.map { it.asTranslationArgument() })