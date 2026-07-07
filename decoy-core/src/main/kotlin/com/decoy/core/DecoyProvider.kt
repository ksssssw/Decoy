package com.decoy.core

/** Holds the runtime [Decoy] instance. Populated by the runtime artifact's initializer. */
public object DecoyProvider {
    public lateinit var instance: Decoy

    public fun isInitialized(): Boolean = ::instance.isInitialized
}
