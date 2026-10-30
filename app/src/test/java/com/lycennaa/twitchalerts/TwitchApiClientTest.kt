package com.lycaennaa.twitchalerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class TwitchApiClientTest {
    @Test
    fun trimsWhitespaceAndOptionalOAuthPrefix() {
        assertEquals("token-value", TwitchApiClient.normalizeToken("  OAuth:token-value\n"))
    }

    @Test
    fun leavesBareAccessTokenUnchangedExceptForWhitespace() {
        assertEquals("token-value", TwitchApiClient.normalizeToken(" token-value "))
    }

    @Test
    fun includesUnderlyingNetworkFailureInStatus() {
        assertEquals(
            "Could not reach Twitch: DNS lookup failed.",
            TwitchApiClient.networkFailureMessage(IOException("DNS lookup failed")),
        )
        assertEquals(
            "Could not reach Twitch: IOException.",
            TwitchApiClient.networkFailureMessage(IOException()),
        )
    }

    @Test
    fun attachesProfileImagesByTwitchUserId() {
        val stream = TwitchStream(
            id = "stream-123",
            userId = "user-456",
            userLogin = "channel",
            userName = "Channel",
            title = "Live",
            gameName = "",
        )
        val missingImageStream = stream.copy(id = "stream-789", userId = "user-789")

        val streamsWithImages = attachProfileImages(
            listOf(stream, missingImageStream),
            mapOf("user-456" to "https://static-cdn.jtvnw.net/user.png"),
        )

        assertEquals("https://static-cdn.jtvnw.net/user.png", streamsWithImages[0].profileImageUrl)
        assertNull(streamsWithImages[1].profileImageUrl)
    }
}
