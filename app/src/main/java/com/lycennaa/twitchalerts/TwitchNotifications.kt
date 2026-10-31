package com.lycaennaa.twitchalerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import java.util.concurrent.Executors

internal object TwitchNotifications {
    const val MONITORING_CHANNEL_ID = "twitch_monitoring"
    private const val LEGACY_LIVE_CHANNEL_ID = "twitch_live_alerts"
    private const val TEST_CHANNEL_ID = "twitch_test_alerts"
    const val MONITORING_NOTIFICATION_ID = 4100
    private const val TEST_NOTIFICATION_ID = 4101
    const val RESUME_REMINDER_NOTIFICATION_ID = 4102
    private const val LIVE_STREAM_NOTIFICATION_ID = 5_000
    private const val DISABLED_STREAMER_IDS_KEY = "disabled_streamer_notification_ids"
    private const val EXTRA_LIVE_STREAM_ID = "twitch_live_stream_id"

    private val streamerNotificationLock = Any()
    private val latestLiveStreamIds = HashMap<String, String>()

    private val avatarExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "twitch-notification-avatar").apply { isDaemon = true }
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MONITORING_CHANNEL_ID,
                "Twitch monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing status while followed channels are being checked."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                TEST_CHANNEL_ID,
                "Twitch test alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "One-time test notifications."
            },
        )
        manager.deleteNotificationChannel(LEGACY_LIVE_CHANNEL_ID)
    }
    fun createStreamerChannels(context: Context, channels: List<TwitchFollowedChannel>) {
        channels.forEach { channel ->
            createStreamerChannel(context, channel.userId, channel.displayName, channel.userLogin)
        }
    }

    private fun createStreamerChannel(
        context: Context,
        userId: String,
        displayName: String,
        userLogin: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || userId.isBlank()) return
        val channelName = if (userLogin.isBlank()) displayName else "$displayName (@$userLogin)"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                streamerChannelId(userId),
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Go-live alerts for @$userLogin."
            },
        )
    }

    fun isStreamerEnabled(context: Context, userId: String): Boolean {
        val disabledIds = context.getSharedPreferences(
            TwitchAppState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getStringSet(DISABLED_STREAMER_IDS_KEY, emptySet()).orEmpty()
        return TwitchNotificationState.shouldNotifyStreamer(userId, disabledIds)
    }

    fun setStreamerEnabled(context: Context, userId: String, enabled: Boolean) {
        synchronized(streamerNotificationLock) {
            val preferences = context.getSharedPreferences(
                TwitchAppState.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            val disabledIds = preferences
                .getStringSet(DISABLED_STREAMER_IDS_KEY, emptySet())
                .orEmpty()
                .toMutableSet()
            if (enabled) disabledIds.remove(userId) else disabledIds.add(userId)
            preferences.edit().putStringSet(DISABLED_STREAMER_IDS_KEY, disabledIds).apply()
                if (!enabled) latestLiveStreamIds.remove(userId)
            if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(userId, LIVE_STREAM_NOTIFICATION_ID)
                val channelId = streamerChannelId(userId)
                manager.activeNotifications
                    .filter { it.notification.channelId == channelId }
                    .forEach { manager.cancel(it.tag, it.id) }
            }
        }
    }

    private fun streamerChannelId(userId: String) = "twitch_streamer_$userId"

    fun notificationsAllowed(context: Context): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    fun monitoring(context: Context, status: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TwitchMonitorService::class.java).setAction(TwitchMonitorService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = openAppIntent(context)
        return Notification.Builder(context, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("Twitch monitoring active")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    fun liveStream(
        context: Context,
        stream: TwitchStream,
        largeIcon: Bitmap? = null,
    ): Notification {
        val builder = Notification.Builder(context, streamerChannelId(stream.userId))
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("${stream.displayName} is live")
            .setContentText(stream.title.ifBlank { "Started streaming on Twitch." })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
        if (largeIcon != null) {
            TwitchStatusBarIcon.fromAvatar(context, largeIcon)?.let(builder::setSmallIcon)
            builder.setLargeIcon(largeIcon)
        }
        if (stream.gameName.isNotBlank()) builder.setSubText(stream.gameName)
        return builder.build().apply {
            extras.putString(EXTRA_LIVE_STREAM_ID, stream.id)
        }
    }

    fun postLiveStreams(context: Context, streams: List<TwitchStream>, session: TwitchSession) {
        if (streams.isEmpty()) return
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        streams.forEach { ensureStreamerChannel(applicationContext, it) }
        val enabledStreams = streams.filter { isStreamerEnabled(applicationContext, it.userId) }
        if (enabledStreams.isEmpty()) return
        for (stream in enabledStreams) {
            postLiveStreamIfEnabled(applicationContext, manager, stream)
        }

        avatarExecutor.execute {
            val profileImagesByUserId = try {
                TwitchApiClient().profileImageUrls(session, enabledStreams.map { it.userId })
            } catch (_: TwitchApiException) {
                for (stream in enabledStreams) clearLatestLiveStreamId(stream)
                return@execute
            }
            val streamsWithProfileImages = attachProfileImages(enabledStreams, profileImagesByUserId)
            for (stream in streamsWithProfileImages) {
                try {
                    if (isStreamerEnabled(applicationContext, stream.userId)) {
                        val avatar = TwitchAvatarLoader.load(stream.profileImageUrl)
                        if (avatar != null) {
                            postLiveStreamIfEnabled(
                                applicationContext,
                                manager,
                                stream,
                                avatar,
                                onlyIfActive = true,
                            )
                        }
                    }
                } finally {
                    clearLatestLiveStreamId(stream)
                }
            }
        }
    }

    private fun ensureStreamerChannel(context: Context, stream: TwitchStream) {
        createStreamerChannel(context, stream.userId, stream.displayName, stream.userLogin)
    }

    private fun postLiveStreamIfEnabled(
        context: Context,
        manager: NotificationManager,
        stream: TwitchStream,
        largeIcon: Bitmap? = null,
        onlyIfActive: Boolean = false,
    ) {
        synchronized(streamerNotificationLock) {
            if (!isStreamerEnabled(context, stream.userId)) return
            val notificationId = LIVE_STREAM_NOTIFICATION_ID
            if (onlyIfActive &&
                manager.activeNotifications.none {
                    it.tag == stream.userId &&
                        it.id == notificationId &&
                        TwitchNotificationState.isNotificationForStream(
                            it.notification.extras.getString(EXTRA_LIVE_STREAM_ID),
                    latestLiveStreamIds[stream.userId],
                            stream.id,
                        )
                }
            ) return
            if (!onlyIfActive) latestLiveStreamIds[stream.userId] = stream.id
            manager.notify(stream.userId, notificationId, liveStream(context, stream, largeIcon))
        }
    }
    private fun clearLatestLiveStreamId(stream: TwitchStream) {
        synchronized(streamerNotificationLock) {
            if (latestLiveStreamIds[stream.userId] == stream.id) {
                latestLiveStreamIds.remove(stream.userId)
            }
        }
    }



    fun postTest(context: Context) {
        val notification = Notification.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("Twitch notification test")
            .setContentText("Test notifications are working.")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(TEST_NOTIFICATION_ID, notification)
    }

    fun postResumeReminder(context: Context): Boolean {
        if (!notificationsAllowed(context)) return false
        createChannels(context)
        val notification = Notification.Builder(context, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("Resume Twitch monitoring")
            .setContentText("Open the app to resume faster checks. Background checks may be delayed.")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(RESUME_REMINDER_NOTIFICATION_ID, notification)
        return true
    }
    fun cancelResumeReminder(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(RESUME_REMINDER_NOTIFICATION_ID)
    }


    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
