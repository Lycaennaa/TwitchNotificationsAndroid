package com.lycaennaa.twitchalerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal object TwitchNotifications {
    const val MONITORING_CHANNEL_ID = "twitch_monitoring"
    const val LIVE_CHANNEL_ID = "twitch_live_alerts"
    const val MONITORING_NOTIFICATION_ID = 4100
    private const val TEST_NOTIFICATION_ID = 4101

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

    fun liveStream(context: Context, stream: TwitchStream): Notification {
        val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twitch_notification)
            .setContentTitle("${stream.displayName} is live")
            .setContentText(stream.title.ifBlank { "Started streaming on Twitch." })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
        if (stream.gameName.isNotBlank()) builder.setSubText(stream.gameName)
        return builder.build()
    }

    fun postLiveStream(context: Context, stream: TwitchStream) {
        val notificationId = 5_000 + (stream.id.hashCode() and 0x7fff_ffff) % 1_000_000
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, liveStream(context, stream))
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

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
