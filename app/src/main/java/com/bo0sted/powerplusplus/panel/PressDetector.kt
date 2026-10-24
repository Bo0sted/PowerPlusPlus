package com.bo0sted.powerplusplus.panel

/**
 * Turns volume key presses into single and double presses. A key without a double press is acted on
 * at once; a key with one waits [windowMs] for a second press, then counts as a single press
 * ([deadline], [expire]). Pressing the other key meanwhile settles the waiting one as a single press
 * first. Times are in milliseconds on one clock (e.g. `KeyEvent.getEventTime`). Pure; unit-tested.
 */
internal class PressDetector(private val windowMs: Long) {

    enum class Key { Up, Down }

    data class Press(val key: Key, val double: Boolean)

    private var waiting: Key? = null
    private var waitingSince = 0L

    /**
     * [key] went down at [now]; [hasDoublePress] says whether a double press is assigned to it.
     * Returns the presses to act on now, in order.
     */
    fun down(key: Key, now: Long, hasDoublePress: Boolean): List<Press> {
        val settled = ArrayList<Press>(2)
        val previous = waiting
        if (previous != null) {
            waiting = null
            if (previous == key && now - waitingSince <= windowMs) return listOf(Press(key, double = true))
            settled += Press(previous, double = false)
        }
        if (hasDoublePress) {
            waiting = key
            waitingSince = now
        } else {
            settled += Press(key, double = false)
        }
        return settled
    }

    /** When the waiting press becomes a single press, or `null` if none is waiting. */
    val deadline: Long? get() = waiting?.let { waitingSince + windowMs }

    /** The waiting press as a single press, once [deadline] has passed. */
    fun expire(now: Long): Press? {
        val key = waiting ?: return null
        if (now < waitingSince + windowMs) return null
        waiting = null
        return Press(key, double = false)
    }
}
