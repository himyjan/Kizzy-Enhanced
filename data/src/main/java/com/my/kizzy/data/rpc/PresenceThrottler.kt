package com.my.kizzy.data.rpc

import kizzy.gateway.entities.presence.Presence

/**
 * Decides when a combined presence should be sent to the gateway.
 *
 * - A real content change (track, artist, RPC fields, buttons, assets) or a
 *   timeline jump (seek / next song) is sent as soon as the minimum interval
 *   allows.
 * - Repeated, essentially identical updates are held until the maximum
 *   interval, so an idle RPC does not spam the socket or waste network.
 */
class PresenceThrottler(
    private val minIntervalMs: Long = 500L,
    private val maxIntervalMs: Long = 15_000L,
    private val timestampToleranceMs: Long = 3_000L,
) {
    private var lastSent: Presence? = null
    private var lastSentAt = 0L

    /** Returns the delay in ms before [presence] should be sent, or -1 to send now. */
    fun delayFor(now: Long, presence: Presence): Long {
        val previous = lastSent ?: return 0L
        val elapsed = now - lastSentAt
        if (elapsed >= maxIntervalMs) return 0L

        val changed = !sameContent(previous, presence) || timelineJumped(previous, presence)
        if (!changed) return maxOf(0L, maxIntervalMs - elapsed)

        return maxOf(0L, minIntervalMs - elapsed)
    }

    fun markSent(now: Long, presence: Presence) {
        lastSent = presence
        lastSentAt = now
    }

    private fun sameContent(a: Presence, b: Presence): Boolean {
        fun strip(p: Presence) = p.copy(
            activities = p.activities?.map { activity ->
                activity?.copy(timestamps = null)
            },
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
