package com.glomdom.splinter.link

interface LinkSource : Linkable {
    val linkRange: Int
    val isLinked: Boolean

    fun onLinked(target: LinkTarget) {}
    fun onUnlinked(target: LinkTarget) {}
}