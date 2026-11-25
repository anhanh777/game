package com.j2me.multibox.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectWatchdogTest {

    @Test
    fun test_watchdog_exponential_backoff_on_repeated_disconnects() = runTest {
        val delays = mutableListOf<Long>()
        var relaunchCount = 0

        val watchdog = ReconnectWatchdog(
            initialBackoffMs = 2000L,
            maxBackoffMs = 16000L,
            onDelay = { ms -> delays.add(ms) },
            onRelaunchRequested = { relaunchCount++ }
        )

        watchdog.triggerReconnectNow()
        watchdog.triggerReconnectNow()
        watchdog.triggerReconnectNow()
        watchdog.triggerReconnectNow()
        watchdog.triggerReconnectNow()

        assertEquals(5, relaunchCount)
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 16000L), delays)

        // Successful network activity resets backoff
        watchdog.onNetworkActivity()
        watchdog.triggerReconnectNow()
        assertEquals(2000L, delays.last())
    }
}
