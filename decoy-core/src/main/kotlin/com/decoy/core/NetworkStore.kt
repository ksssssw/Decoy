package com.decoy.core

import java.util.concurrent.CopyOnWriteArrayList

/** In-memory ring buffer of captured traffic. For SDK-internal use. */
public object NetworkStore {
    private const val MAX_SIZE = 500
    // Each entry can carry a 1 MB request body plus a 1 MB response body
    // (UTF-16 doubles that on the heap), so a count cap alone would let the
    // buffer grow to gigabytes inside the host app. Evict on total size too.
    private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024

    private val calls = ArrayDeque<CapturedRequest>()
    private var totalBytes = 0L
    private val listeners = CopyOnWriteArrayList<(CapturedRequest) -> Unit>()

    public fun add(call: CapturedRequest) {
        synchronized(this) {
            calls.addLast(call)
            totalBytes += call.approxBytes()
            // The newest entry always survives, even if it alone busts the budget.
            while ((calls.size > MAX_SIZE || totalBytes > MAX_TOTAL_BYTES) && calls.size > 1) {
                totalBytes -= calls.removeFirst().approxBytes()
            }
        }
        // Notify outside the lock — a slow listener must never stall capture
        // threads, and a throwing one must never fail the capture that's being
        // recorded or starve the listeners after it.
        listeners.forEach { listener -> runCatching { listener(call) } }
    }

    @Synchronized public fun getAll(): List<CapturedRequest> = calls.toList().reversed()

    @Synchronized public fun getById(id: String): CapturedRequest? = calls.find { it.id == id }

    @Synchronized public fun clear() {
        calls.clear()
        totalBytes = 0L
    }

    public fun addListener(listener: (CapturedRequest) -> Unit) {
        listeners.add(listener)
    }

    public fun removeListener(listener: (CapturedRequest) -> Unit) {
        listeners.remove(listener)
    }

    // UTF-16 chars are 2 bytes; the constant absorbs object headers and the
    // small fixed fields. An estimate is fine — the budget guards magnitude.
    private fun CapturedRequest.approxBytes(): Long =
        2L * (
            url.length +
                (requestBody?.length ?: 0) +
                (responseBody?.length ?: 0) +
                requestHeaders.entries.sumOf { it.key.length + it.value.length } +
                responseHeaders.entries.sumOf { it.key.length + it.value.length }
            ) + 512
}
