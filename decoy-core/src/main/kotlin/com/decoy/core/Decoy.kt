package com.decoy.core

/** Controls the Decoy inspector server lifecycle. Implemented by the runtime artifact. */
public interface Decoy {
    public fun start(port: Int = 8090)
    public fun stop()
    public fun isRunning(): Boolean
    public fun getPort(): Int
}
