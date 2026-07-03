package com.peekaboo.core

/** Controls the Peekaboo inspector server lifecycle. Implemented by the runtime artifact. */
public interface Peekaboo {
    public fun start(port: Int = 8090)
    public fun stop()
    public fun isRunning(): Boolean
    public fun getPort(): Int
}
