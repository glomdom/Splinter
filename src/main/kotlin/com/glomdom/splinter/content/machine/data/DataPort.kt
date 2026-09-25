package com.glomdom.splinter.content.machine.data

import org.bukkit.block.BlockFace

class DataPort(val owner: DataEndpoint, val face: BlockFace, val kind: Kind) {
    enum class Kind { INPUT, OUTPUT }

    var value = 0L
        private set

    var peer: DataPort? = null

    fun emit(v: Long) {
        if (v == value) return

        value = v
        peer?.receive(v)
    }

    fun receive(v: Long) {
        if (v == value) return

        value = v
        owner.onInput(this)
    }

    fun link(other: DataPort) {
        unlink()
        other.unlink()

        peer = other
        other.peer = this

        val output = if (kind == Kind.OUTPUT) this else other
        val input = if (kind == Kind.INPUT) this else other

        input.receive(output.value)
    }

    fun unlink() {
        val other = peer ?: return

        peer = null
        other.peer = null

        val input = if (kind == Kind.INPUT) this else other
        input.receive(0)
    }
}
