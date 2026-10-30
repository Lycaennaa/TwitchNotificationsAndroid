package com.lycaennaa.twitchalerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchMonitorPollerTest {
    @Test
    fun rejectsPollResultsAfterTheSavedTokenChanges() {
        val session = TwitchSession(
            accessToken = "old-token",
            clientId = "client-id",
            userId = "old-user",
        )

        assertFalse(matchesTokenSnapshot(session.accessToken, "new-token"))
    }

    @Test
    fun rejectsPollResultsAfterTheSavedTokenIsRemoved() {
        val session = TwitchSession(
            accessToken = "old-token",
            clientId = "client-id",
            userId = "old-user",
        )

        assertFalse(matchesTokenSnapshot(session.accessToken, null))
    }

    @Test
    fun acceptsPollResultsWhenTheSavedTokenIsUnchanged() {
        val session = TwitchSession(
            accessToken = "current-token",
            clientId = "client-id",
            userId = "current-user",
        )

        assertTrue(matchesTokenSnapshot(session.accessToken, "current-token"))
    }

    @Test
    fun rejectsAnEmptyPollSnapshotAfterATokenIsSaved() {
        assertFalse(matchesTokenSnapshot(null, "new-token"))
    }

    @Test
    fun acceptsAnEmptyPollSnapshotWhileNoTokenIsSaved() {
        assertTrue(matchesTokenSnapshot(null, null))
    }

}
