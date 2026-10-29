package com.lycaennaa.twitchalerts

import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchApiClientTest {
    @Test
    fun trimsWhitespaceAndOptionalOAuthPrefix() {
        assertEquals("token-value", TwitchApiClient.normalizeToken("  OAuth:token-value\n"))
    }

    @Test
    fun leavesBareAccessTokenUnchangedExceptForWhitespace() {
        assertEquals("token-value", TwitchApiClient.normalizeToken(" token-value "))
    }
}
