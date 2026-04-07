package com.peekaboo.android

import com.peekaboo.core.PeekabooProvider

object PeekabooLauncher {
    /** Returns the inspector URL if Peekaboo is running, null otherwise. */
    fun getInspectorUrl(): String? {
        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) return null
        return "http://localhost:${PeekabooProvider.instance.getPort()}"
    }
}
