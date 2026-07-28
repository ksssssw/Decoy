package com.decoy.core

/** Holds the runtime [Decoy] instance. Populated by the runtime artifact's initializer. */
public object DecoyProvider {
    // Written once on the initializer thread, read from arbitrary network
    // threads — @Volatile makes the publication safe.
    @Volatile
    public lateinit var instance: Decoy

    public fun isInitialized(): Boolean = ::instance.isInitialized
}
