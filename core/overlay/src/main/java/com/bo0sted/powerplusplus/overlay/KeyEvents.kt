package com.bo0sted.powerplusplus.overlay

import android.view.KeyEvent

/**
 * Implemented by Power++'s accessibility service: hands key presses to a filter before any app gets
 * them (`AccessibilityService.onKeyEvent`). The service asks the system for key events only while a
 * filter is set; otherwise it never sees them. Main thread only.
 */
interface KeyEvents {
    /**
     * Sets the filter, or removes it with `null`. The filter returns true to consume an event; the
     * system waits at most 500 ms for it, so it must return quickly.
     */
    fun setKeyEventFilter(filter: ((KeyEvent) -> Boolean)?)
}
