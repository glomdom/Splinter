package com.glomdom.splinter.data

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class DataDisconnectEvent(
    val block1: DataNode,
    val block2: DataNode
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList: HandlerList = HandlerList()
    }
}