package com.lycaennaa.twitchalerts

internal object TwitchNotificationState {
    fun newlyLiveStreams(
        currentStreams: List<TwitchStream>,
        previousStreamIds: Set<String>?,
    ): List<TwitchStream> {
        if (previousStreamIds == null) return emptyList()
        return currentStreams.filterNot { it.id in previousStreamIds }
    }
}
