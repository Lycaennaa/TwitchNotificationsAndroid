package com.lycaennaa.twitchalerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchAppStateTest {
    @Test
    fun resumesMonitoringAfterTimeoutWhenStillRequested() {
        assertTrue(
            TwitchAppState.shouldResumeAfterTimeout(
                monitoringRequested = true,
                resumeAfterTimeout = true,
            ),
        )
    }

    @Test
    fun doesNotResumeAfterTimeoutWhenMonitoringWasStopped() {
        assertFalse(
            TwitchAppState.shouldResumeAfterTimeout(
                monitoringRequested = false,
                resumeAfterTimeout = true,
            ),
        )
    }

    @Test
    fun doesNotResumeAfterTimeoutWhenTimeoutFlagIsClear() {
        assertFalse(
            TwitchAppState.shouldResumeAfterTimeout(
                monitoringRequested = true,
                resumeAfterTimeout = false,
            ),
        )
    }

    @Test
    fun startsOnBootOnlyWhenEnabledAndMonitoringWasRequested() {
        assertTrue(
            TwitchAppState.shouldStartMonitoringOnBoot(
                autoStartEnabled = true,
                monitoringRequested = true,
            ),
        )
    }

    @Test
    fun doesNotStartOnBootWhenAutoStartIsDisabled() {
        assertFalse(
            TwitchAppState.shouldStartMonitoringOnBoot(
                autoStartEnabled = false,
                monitoringRequested = true,
            ),
        )
    }

    @Test
    fun doesNotStartOnBootWhenMonitoringWasStopped() {
        assertFalse(
            TwitchAppState.shouldStartMonitoringOnBoot(
                autoStartEnabled = true,
                monitoringRequested = false,
            ),
        )
    }


    @Test
    fun skipsFallbackWhileServiceHeartbeatIsFresh() {
        assertFalse(
            TwitchAppState.shouldRunBackgroundFallback(
                monitoringRequested = true,
                lastHeartbeatMillis = 100_000L,
                nowMillis = 219_999L,
                staleAfterMillis = 120_000L,
            ),
        )
    }

    @Test
    fun runsFallbackWhenServiceHeartbeatIsStale() {
        assertTrue(
            TwitchAppState.shouldRunBackgroundFallback(
                monitoringRequested = true,
                lastHeartbeatMillis = 100_000L,
                nowMillis = 220_000L,
                staleAfterMillis = 120_000L,
            ),
        )
    }

    @Test
    fun runsFallbackWhenNoServiceHeartbeatExists() {
        assertTrue(
            TwitchAppState.shouldRunBackgroundFallback(
                monitoringRequested = true,
                lastHeartbeatMillis = 0L,
                nowMillis = 220_000L,
                staleAfterMillis = 120_000L,
            ),
        )
    }

    @Test
    fun skipsFallbackWhenMonitoringWasStopped() {
        assertFalse(
            TwitchAppState.shouldRunBackgroundFallback(
                monitoringRequested = false,
                lastHeartbeatMillis = 0L,
                nowMillis = 220_000L,
                staleAfterMillis = 120_000L,
            ),
        )
    }

}
