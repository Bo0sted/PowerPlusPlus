package com.bo0sted.powerplusplus.overlay

/**
 * Implemented by Power++'s accessibility service: calls listeners on every `TYPE_WINDOWS_CHANGED`
 * event (a window added, removed or moved, e.g. the keyboard opening). The service subscribes to
 * those events only while a listener is registered. Main thread only.
 */
interface WindowChanges {
    fun addWindowsChangedListener(listener: Runnable)

    fun removeWindowsChangedListener(listener: Runnable)
}
