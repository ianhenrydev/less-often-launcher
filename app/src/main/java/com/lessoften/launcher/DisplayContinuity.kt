package com.lessoften.launcher

enum class DisplayContext { Restricted, Permitted, Uncertain, Inactive }

/** A missing activity root is not positive evidence that the Restricted context was left. */
class DisplayContinuity(initiallyRestricted: Boolean = false, private val graceMillis: Long = 1000) {
    private var restricted = initiallyRestricted
    private var uncertainSince: Long? = null

    fun useGrayscale(context: DisplayContext, now: Long): Boolean = when (context) {
        DisplayContext.Restricted -> { restricted = true; uncertainSince = null; true }
        DisplayContext.Permitted, DisplayContext.Inactive -> { reset(); false }
        DisplayContext.Uncertain -> {
            if (restricted && uncertainSince == null) uncertainSince = now
            if (uncertainSince?.let { now - it >= graceMillis } == true) restricted = false
            restricted
        }
    }

    fun recheckDelay(now: Long): Long? = if (restricted) uncertainSince?.let { (it + graceMillis - now).coerceAtLeast(1) } else null
    fun reset() { restricted = false; uncertainSince = null }
}
