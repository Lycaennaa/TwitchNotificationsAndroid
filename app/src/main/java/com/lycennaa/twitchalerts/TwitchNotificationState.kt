package com.lycaennaa.twitchalerts

internal object TwitchNotificationState {
    fun shouldNotifyStreamer(userId: String, disabledUserIds: Set<String>): Boolean =
        userId !in disabledUserIds
    fun isNotificationForStream(
        activeStreamId: String?,
        latestStreamId: String?,
        streamId: String,
    ): Boolean = activeStreamId == streamId && latestStreamId == streamId


    fun newlyLiveStreams(
        currentStreams: List<TwitchStream>,
        previousStreamIds: Set<String>?,
    ): List<TwitchStream> {
        if (previousStreamIds == null) return emptyList()
        return currentStreams.filterNot { it.id in previousStreamIds }
    }
}
