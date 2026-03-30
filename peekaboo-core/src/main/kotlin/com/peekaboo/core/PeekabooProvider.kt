package com.peekaboo.core

object PeekabooProvider {
    lateinit var instance: Peekaboo

    fun isInitialized(): Boolean = ::instance.isInitialized
}
