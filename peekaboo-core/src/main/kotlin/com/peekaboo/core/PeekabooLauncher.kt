package com.peekaboo.core

/**
 * Entry point for opening the Peekaboo inspector from the host app.
 *
 * Safe to call from any build type: returns null when the inspector is not
 * running — e.g. in release builds that use the no-op artifacts.
 */
public object PeekabooLauncher {
    public fun getInspectorUrl(): String? {
        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) return null
        return "http://localhost:${PeekabooProvider.instance.getPort()}"
    }
}
