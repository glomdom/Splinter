package com.glomdom.splinter.interfaces

interface LinkSource : Linkable {
    val linkRange: Int
    val isLinked: Boolean

    fun onLinked(target: LinkTarget) {}
    fun onUnlinked(target: LinkTarget) {}
}