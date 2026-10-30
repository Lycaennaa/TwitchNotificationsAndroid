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
    const val LIVE_CHANNEL_ID = "twitch_live_alerts"
    const val MONITORING_NOTIFICATION_ID = 4100
    private const val TEST_NOTIFICATION_ID = 4101
    const val RESUME_REMINDER_NOTIFICATION_ID = 4102
    private const val LIVE_STREAM_NOTIFICATION_ID = 5_000

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
                LIVE_CHANNEL_ID,
                "Twitch go-live alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when a followed Twitch channel starts streaming."
            },
        )
    }

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
        val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("${stream.displayName} is live")
            .setContentText(stream.title.ifBlank { "Started streaming on Twitch." })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        if (stream.gameName.isNotBlank()) builder.setSubText(stream.gameName)
        return builder.build()
    }

    fun postLiveStreams(context: Context, streams: List<TwitchStream>, session: TwitchSession) {
        if (streams.isEmpty()) return
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        for (stream in streams) {
            manager.notify(stream.id, LIVE_STREAM_NOTIFICATION_ID, liveStream(applicationContext, stream))
        }

        avatarExecutor.execute {
            val profileImagesByUserId = try {
                TwitchApiClient().profileImageUrls(session, streams.map { it.userId })
            } catch (_: TwitchApiException) {
                return@execute
            }
            val streamsWithProfileImages = attachProfileImages(streams, profileImagesByUserId)
            for (stream in streamsWithProfileImages) {
                val avatar = TwitchAvatarLoader.load(stream.profileImageUrl) ?: continue
                val notificationId = LIVE_STREAM_NOTIFICATION_ID
                if (manager.activeNotifications.none { it.tag == stream.id && it.id == notificationId }) continue
                manager.notify(stream.id, notificationId, liveStream(applicationContext, stream, avatar))
            }
        }
    }


    fun postTest(context: Context) {
        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
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
