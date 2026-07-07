package com.decoy.core

/**
 * Entry point for opening the Decoy inspector from the host app.
 *
 * Safe to call from any build type: returns null when the inspector is not
 * running — e.g. in release builds that use the no-op artifacts.
 */
public object DecoyLauncher {
    public fun getInspectorUrl(): String? {
        if (!DecoyProvider.isInitialized() || !DecoyProvider.instance.isRunning()) return null
        return "http://localhost:${DecoyProvider.instance.getPort()}"
    }
}
