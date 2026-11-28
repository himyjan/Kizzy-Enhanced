package com.my.kizzy.feature_rpc_base.services

import kotlinx.coroutines.delay

/**
 * Coordinates RPC dispatch pacing:
 * - At least [minIntervalMs] (500 ms) between actual sends when changed or forced.
 * - At most [maxIdleIntervalMs] (14,500 ms) between sends when unchanged.
 */
class RpcPacer(
    private val minIntervalMs: Long = 500L,
    private val maxIdleIntervalMs: Long = 14_500L,
) {
    private var lastSentAtMs: Long = 0L

    fun reset() {
        lastSentAtMs = 0L
    }

    suspend fun acquireSendSlot(
        hasMeaningfulChange: Boolean,
        forceRefresh: Boolean = false,
    ): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSentAtMs

        if (
            lastSentAtMs > 0L &&
            !forceRefresh &&
            !hasMeaningfulChange &&
            elapsed < maxIdleIntervalMs
        ) {
            return false
        }

        if (lastSentAtMs > 0L && elapsed < minIntervalMs) {
            delay(minIntervalMs - elapsed)
        }

        lastSentAtMs = System.currentTimeMillis()
        return true
    }
}