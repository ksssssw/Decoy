package com.peekaboo.core

import java.util.concurrent.CopyOnWriteArrayList

object NetworkStore {
    private const val MAX_SIZE = 500
    private val calls = ArrayDeque<CapturedRequest>()
    private val listeners = CopyOnWriteArrayList<(CapturedRequest) -> Unit>()

    @Synchronized
    fun add(call: CapturedRequest) {
        if (calls.size >= MAX_SIZE) calls.removeFirst()
        calls.addLast(call)
        listeners.forEach { it(call) }
    }

    @Synchronized fun getAll(): List<CapturedRequest> = calls.toList().reversed()

    @Synchronized fun getById(id: String): CapturedRequest? = calls.find { it.id == id }

    @Synchronized fun clear() = calls.clear()

    fun addListener(listener: (CapturedRequest) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (CapturedRequest) -> Unit) = listeners.remove(listener)
}
