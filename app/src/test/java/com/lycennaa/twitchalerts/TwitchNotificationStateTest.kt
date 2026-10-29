package com.lycaennaa.twitchalerts

import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchNotificationStateTest {
    @Test
    fun firstFetchCreatesBaselineWithoutAlertingForAlreadyLiveStreams() {
        val current = listOf(stream("already-live"))

        val newlyLive = TwitchNotificationState.newlyLiveStreams(current, previousStreamIds = null)

        assertEquals(emptyList<TwitchStream>(), newlyLive)
    }

    @Test
    fun onlyStreamsAbsentFromPreviousPollAreReportedAsNewlyLive() {
        val alreadyLive = stream("already-live")
        val startedBetweenPolls = stream("just-started")
        val current = listOf(alreadyLive, startedBetweenPolls)

        val newlyLive = TwitchNotificationState.newlyLiveStreams(
            current,
            previousStreamIds = setOf(alreadyLive.id),
        )

        assertEquals(listOf(startedBetweenPolls), newlyLive)
    }

    @Test
    fun aStreamCanBeReportedAgainAfterAnOfflinePoll() {
        val stream = stream("stream-1")

        val afterOfflinePoll = TwitchNotificationState.newlyLiveStreams(
            listOf(stream),
            previousStreamIds = emptySet(),
        )

        assertEquals(listOf(stream), afterOfflinePoll)
    }

    private fun stream(id: String) = TwitchStream(
        id = id,
        userId = "user-$id",
        userLogin = id,
        userName = id,
        title = "Playing",
        gameName = "Game",
    )
}
