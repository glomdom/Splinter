package com.glomdom.splinter.content.tool

import com.glomdom.splinter.interfaces.LinkResult
import com.glomdom.splinter.interfaces.LinkSource
import com.glomdom.splinter.interfaces.LinkTarget
import com.glomdom.splinter.interfaces.Linkable

object Linking {
    fun link(a: Linkable, b: Linkable): LinkResult {
        if (a.block == b.block) return LinkResult.SameBlock
        if (a.block.world != b.block.world) return LinkResult.DifferentWorld

        val (source, target) = when (a) {
            is LinkSource if b is LinkTarget -> a to b
            is LinkTarget if b is LinkSource -> b to a

            else -> return LinkResult.IncompatiblePair
        }

        if (target.hasSource(source)) {
            target.removeSource(source)
            source.onUnlinked(target)

            return LinkResult.Unlinked(source, target)
        }

        if (source.isLinked) return LinkResult.SourceBusy(source)

        val distance = source.block.location.distance(target.block.location).toInt()
        if (distance > source.linkRange) {
            return LinkResult.TooFar(distance, source.linkRange)
        }

        if (target.sourceCount >= target.linkCapacity) {
            return LinkResult.CapacityFull(target, target.linkCapacity)
        }

        target.addSource(source)
        source.onLinked(target)

        return LinkResult.Linked(source, target)
    }
}