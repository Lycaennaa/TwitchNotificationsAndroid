package com.lycaennaa.twitchalerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TwitchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = context.getSharedPreferences(TwitchAppState.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val autoStartEnabled = preferences.getBoolean(TwitchAppState.KEY_AUTO_START_ON_BOOT, false)
        val monitoringRequested = preferences.getBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
        if (!TwitchAppState.shouldStartMonitoringOnBoot(autoStartEnabled, monitoringRequested)) {
            TwitchMonitoringWork.cancelFallback(context)
            TwitchMonitoringWork.cancelResumeReminder(context)
            TwitchNotifications.cancelResumeReminder(context)
            if (monitoringRequested) {
                preferences.edit()
                    .putString(TwitchAppState.KEY_STATUS, "Monitoring paused after reboot. Open the app to start again.")
                    .apply()
            }
            return
        }

        try {
            context.startForegroundService(Intent(context, TwitchMonitorService::class.java))
        } catch (exception: Exception) {
            preferences.edit()
                .putString(
                    TwitchAppState.KEY_STATUS,
                    "Android could not resume monitoring after reboot. Open the app to start again.",
                )
                .apply()
        }
    }
}
