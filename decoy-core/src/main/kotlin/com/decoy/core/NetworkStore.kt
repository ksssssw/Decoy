package com.decoy.core

import java.util.concurrent.CopyOnWriteArrayList

/** In-memory ring buffer of captured traffic. For SDK-internal use. */
public object NetworkStore {
    private const val MAX_SIZE = 500
    private val calls = ArrayDeque<CapturedRequest>()
    private val listeners = CopyOnWriteArrayList<(CapturedRequest) -> Unit>()

    public fun add(call: CapturedRequest) {
        synchronized(this) {
            if (calls.size >= MAX_SIZE) calls.removeFirst()
            calls.addLast(call)
        }
        // Notify outside the lock — a slow listener must never stall capture threads.
        listeners.forEach { it(call) }
    }

    @Synchronized public fun getAll(): List<CapturedRequest> = calls.toList().reversed()

    @Synchronized public fun getById(id: String): CapturedRequest? = calls.find { it.id == id }

    @Synchronized public fun clear(): Unit = calls.clear()

    public fun addListener(listener: (CapturedRequest) -> Unit) {
        listeners.add(listener)
    }

    public fun removeListener(listener: (CapturedRequest) -> Unit) {
        listeners.remove(listener)
    }
}
