package com.glomdom.splinter.link

import com.glomdom.splinter.util.tr
import net.kyori.adventure.text.Component

/** Outcome of [Linking.link], shown to the player on the action bar. */
sealed interface LinkResult {
    val message: Component

    val succeeded: Boolean
        get() = this is Linked || this is Unlinked

    data class Linked(val source: LinkSource, val target: LinkTarget) : LinkResult {
        override val message
            get() = tr(
                "messages.linker.linked",
                "source" to source.displayName,
                "target" to target.displayName,
                "x" to Component.text(target.block.x),
                "y" to Component.text(target.block.y),
                "z" to Component.text(target.block.z),
            )
    }

    data class Unlinked(val source: LinkSource, val target: LinkTarget) : LinkResult {
        override val message
            get() = tr("messages.linker.unlinked", "source" to source.displayName, "target" to target.displayName)
    }

    data class CapacityFull(val target: LinkTarget, val limit: Int) : LinkResult {
        override val message
            get() = tr("messages.linker.capacity_full", "target" to target.displayName, "limit" to Component.text(limit))
    }

    data class TooFar(val distance: Int, val maxRange: Int) : LinkResult {
        override val message
            get() = tr("messages.linker.too_far", "distance" to Component.text(distance), "range" to Component.text(maxRange))
    }

    data class SourceBusy(val source: LinkSource) : LinkResult {
        override val message
            get() = tr("messages.linker.source_busy", "source" to source.displayName)
    }

    data object IncompatiblePair : LinkResult {
        override val message get() = tr("messages.linker.incompatible")
    }

    data object InvalidBlock : LinkResult {
        override val message get() = tr("messages.linker.invalid")
    }

    data object SameBlock : LinkResult {
        override val message get() = tr("messages.linker.same_block")
    }

    data object DifferentWorld : LinkResult {
        override val message get() = tr("messages.linker.different_world")
    }
}
