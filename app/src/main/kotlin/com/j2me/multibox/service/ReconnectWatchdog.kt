package com.j2me.multibox.service

import kotlinx.coroutines.delay

/**
 * Watches socket/network health and triggers clean MIDlet relaunch with exponential backoff
 * when connection drops unexpectedly.
 */
class ReconnectWatchdog(
    private val initialBackoffMs: Long = 2000L,
    private val maxBackoffMs: Long = 60_000L,
    private val onDelay: suspend (Long) -> Unit = { delay(it) },
    private val onRelaunchRequested: suspend () -> Unit
) {
    private var currentBackoffMs: Long = initialBackoffMs

    fun onNetworkActivity() {
        currentBackoffMs = initialBackoffMs
    }

    suspend fun triggerReconnectNow() {
        val waitMs = currentBackoffMs
        onDelay(waitMs)
        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(maxBackoffMs)
        onRelaunchRequested()
    }
}
