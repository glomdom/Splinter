package com.glomdom.splinter.extensions

import org.bukkit.NamespacedKey

internal operator fun NamespacedKey.plus(string: String): String {
    return this.toString() + string
}
