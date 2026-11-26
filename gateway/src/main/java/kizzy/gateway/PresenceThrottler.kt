package kizzy.gateway

import kizzy.gateway.entities.presence.Presence

/**
 * Dynamic presence send cadence: changes and timeline jumps go out quickly,
 * repeated identical updates are held until a maximum interval.
 */
class PresenceThrottler(
    private val minIntervalMs: Long = 500L,
    private val maxIntervalMs: Long = 15_000L,
    private val timestampToleranceMs: Long = 3_000L,
) {
    private var lastSent: Presence? = null
    private var lastSentAt = 0L

    /** Delay before [presence] should be sent, or 0 to send immediately. */
    fun delayFor(now: Long, presence: Presence): Long {
        val previous = lastSent ?: return 0L
        val elapsed = now - lastSentAt
        if (elapsed >= maxIntervalMs) return 0L
        val changed = !sameContent(previous, presence) || timelineJumped(previous, presence)
        return if (changed) maxOf(0L, minIntervalMs - elapsed)
        else maxOf(0L, maxIntervalMs - elapsed)
    }

    fun markSent(now: Long, presence: Presence) {
        lastSent = presence
        lastSentAt = now
    }

    private fun sameContent(a: Presence, b: Presence): Boolean {
        fun strip(p: Presence) = p.copy(
            activities = p.activities?.map { it?.copy(timestamps = null) },
        )
        return strip(a) == strip(b)
    }

    private fun timelineJumped(a: Presence, b: Presence): Boolean {
        val old = a.activities?.firstOrNull()?.timestamps ?: return false
        val new = b.activities?.firstOrNull()?.timestamps ?: return false
        return kotlin.math.abs((new.start ?: 0L) - (old.start ?: 0L)) > timestampToleranceMs ||
            kotlin.math.abs((new.end ?: 0L) - (old.end ?: 0L)) > timestampToleranceMs
    }
}
