package com.lycaennaa.twitchalerts

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal object TwitchMonitoringWork {
    const val FALLBACK_INTERVAL_MINUTES = 15L
    const val HEARTBEAT_STALE_AFTER_MILLIS = 180_000L
    private const val FALLBACK_WORK_NAME = "twitch-monitor-fallback"
    private const val RESUME_REMINDER_WORK_NAME = "twitch-resume-reminder"

    fun scheduleFallback(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            TwitchFallbackWorker::class.java,
            FALLBACK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(FALLBACK_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleResumeReminder(context: Context) {
        val request = OneTimeWorkRequest.Builder(TwitchResumeReminderWorker::class.java)
            .setInitialDelay(FALLBACK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RESUME_REMINDER_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelFallback(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(FALLBACK_WORK_NAME)
    }

    fun cancelResumeReminder(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RESUME_REMINDER_WORK_NAME)
    }
}

internal class TwitchFallbackWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            TwitchAppState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        if (!monitoringRequested(preferences) || !TwitchMonitorPollGuard.tryAcquire()) return Result.success()

        try {
            if (!fallbackRequired(preferences)) return Result.success()

            val reminderWasPending = preferences.getBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
            preferences.edit()
                .putBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, true)
                .putString(TwitchAppState.KEY_STATUS, TwitchAppState.STATUS_BACKGROUND_FALLBACK)
                .apply()
            if (!reminderWasPending) {
                TwitchMonitoringWork.scheduleResumeReminder(applicationContext)
            }
            sendStatus(TwitchAppState.STATUS_BACKGROUND_FALLBACK)

            if (!TwitchNotifications.notificationsAllowed(applicationContext)) {
                updateStatus(preferences, "Notifications are disabled. Enable them to continue background alerts.")
                return Result.success()
            }

            val poller = TwitchMonitorPoller(applicationContext)
            val outcome = try {
                poller.poll(session = null)
            } catch (exception: TwitchApiException) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!poller.isCurrentPollToken()) return Result.success()
                    return handleApiException(preferences, exception)
                } finally {
                    TwitchAccountStateGuard.release()
                }
            } catch (exception: TokenVaultException) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!poller.isCurrentPollToken()) return Result.success()
                    stopFallback(
                        preferences,
                        exception.message ?: "The saved OAuth token could not be read. Enter it again.",
                    )
                    return Result.success()
                } finally {
                    TwitchAccountStateGuard.release()
                }
            } catch (exception: Exception) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!poller.isCurrentPollToken()) return Result.success()
                    updateStatus(preferences, "Background Twitch check failed. WorkManager will try again later.")
                    return Result.success()
                } finally {
                    TwitchAccountStateGuard.release()
                }
            }

        if (!monitoringRequested(preferences)) return Result.success()
            return when (outcome) {
                is TwitchMonitorPollOutcome.Stop -> {
                    TwitchAccountStateGuard.acquire()
                    try {
                        if (poller.isCurrentPollToken()) {
                            stopFallback(preferences, outcome.status)
                        }
                    } finally {
                        TwitchAccountStateGuard.release()
                    }
                    Result.success()
                }
                is TwitchMonitorPollOutcome.Success -> {
                    TwitchAccountStateGuard.acquire()
                    try {
                        if (
                            !monitoringRequested(preferences) ||
                            !poller.isCurrentSession(outcome.session)
                        ) return Result.success()
                        for (stream in outcome.newlyLiveStreams) {
                            if (!monitoringRequested(preferences)) return Result.success()
                            TwitchNotifications.postLiveStream(applicationContext, stream)
                        }
                        if (monitoringRequested(preferences)) {
                            poller.commit(outcome)
                            updateStatus(preferences, TwitchAppState.STATUS_BACKGROUND_FALLBACK)
                        }
                    } finally {
                        TwitchAccountStateGuard.release()
                    }
                    Result.success()
                }
            }
        } finally {
            TwitchMonitorPollGuard.release()
        }
    }

    private fun handleApiException(
        preferences: SharedPreferences,
        exception: TwitchApiException,
    ): Result {
        val terminalStatus = when (exception.kind) {
            TwitchApiException.Kind.INVALID_TOKEN -> "OAuth token is invalid or expired. Replace it to resume."
            TwitchApiException.Kind.MISSING_SCOPE -> "OAuth token needs the user:read:follows scope."
            else -> null
        }
        if (terminalStatus != null) {
            stopFallback(preferences, terminalStatus)
        } else {
            updateStatus(preferences, "Background Twitch check failed. WorkManager will try again later.")
        }
        return Result.success()
    }

    private fun monitoringRequested(preferences: SharedPreferences) =
        preferences.getBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)

    private fun fallbackRequired(preferences: SharedPreferences) =
        TwitchAppState.shouldRunBackgroundFallback(
            monitoringRequested = monitoringRequested(preferences),
            lastHeartbeatMillis = preferences.getLong(TwitchAppState.KEY_LAST_SERVICE_HEARTBEAT_MILLIS, 0L),
            nowMillis = System.currentTimeMillis(),
            staleAfterMillis = TwitchMonitoringWork.HEARTBEAT_STALE_AFTER_MILLIS,
        )

    private fun stopFallback(preferences: SharedPreferences, status: String) {
        if (!fallbackRequired(preferences)) return
        preferences.edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
            .putBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
            .putString(TwitchAppState.KEY_STATUS, status)
            .apply()
        TwitchMonitoringWork.cancelFallback(applicationContext)
        TwitchMonitoringWork.cancelResumeReminder(applicationContext)
        TwitchNotifications.cancelResumeReminder(applicationContext)
        sendStatus(status)
    }

    private fun updateStatus(preferences: SharedPreferences, status: String) {
        if (!fallbackRequired(preferences)) return
        preferences.edit().putString(TwitchAppState.KEY_STATUS, status).apply()
        sendStatus(status)
    }

    private fun sendStatus(status: String) {
        applicationContext.sendBroadcast(
            Intent(TwitchMonitorService.ACTION_STATUS)
                .setPackage(applicationContext.packageName)
                .putExtra(TwitchMonitorService.EXTRA_STATUS, status),
        )
    }
}

internal class TwitchResumeReminderWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            TwitchAppState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val monitoringRequested = preferences.getBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
        val resumeAfterTimeout = preferences.getBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
        if (TwitchAppState.shouldResumeAfterTimeout(monitoringRequested, resumeAfterTimeout)) {
            TwitchNotifications.postResumeReminder(applicationContext)
        }
        return Result.success()
    }
}
