package com.peekaboo.core

/** Holds the runtime [Peekaboo] instance. Populated by the runtime artifact's initializer. */
public object PeekabooProvider {
    public lateinit var instance: Peekaboo

    public fun isInitialized(): Boolean = ::instance.isInitialized
}
