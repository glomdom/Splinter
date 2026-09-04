package com.glomdom.splinter.interfaces

interface LinkTarget : Linkable {
    val linkCapacity: Int
    val sourceCount: Int

    fun hasSource(source: LinkSource): Boolean
    fun addSource(source: LinkSource)
    fun removeSource(source: LinkSource)
}