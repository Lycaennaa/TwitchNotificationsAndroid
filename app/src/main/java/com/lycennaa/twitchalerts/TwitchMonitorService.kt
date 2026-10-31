package com.lycaennaa.twitchalerts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal object TwitchAppState {
    const val PREFERENCES_NAME = "twitch_monitor_state"
    const val KEY_STATUS = "status"
    const val KEY_MONITORING_REQUESTED = "monitoring_requested"
    const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
    const val KEY_RESUME_AFTER_TIMEOUT = "resume_after_timeout"
    const val KEY_LAST_SERVICE_HEARTBEAT_MILLIS = "last_service_heartbeat_millis"
    const val STATUS_BACKGROUND_FALLBACK =
        "One-minute monitoring paused. Background checks run about every 15 minutes; open the app to resume faster checks."

    const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
    const val KEY_BASELINE_USER_ID = "baseline_user_id"
    const val KEY_LIVE_STREAM_IDS = "live_stream_ids"

    fun shouldResumeAfterTimeout(monitoringRequested: Boolean, resumeAfterTimeout: Boolean) =
        monitoringRequested && resumeAfterTimeout

    fun shouldStartMonitoringOnBoot(autoStartEnabled: Boolean, monitoringRequested: Boolean) =
        autoStartEnabled && monitoringRequested

    fun shouldRunBackgroundFallback(
        monitoringRequested: Boolean,
        lastHeartbeatMillis: Long,
        nowMillis: Long,
        staleAfterMillis: Long,
    ) = monitoringRequested &&
        (lastHeartbeatMillis <= 0L || nowMillis - lastHeartbeatMillis >= staleAfterMillis)
}

class TwitchMonitorService : Service() {
    private val monitorPoller by lazy { TwitchMonitorPoller(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var pollExecutor: ScheduledExecutorService
    private var pollTask: ScheduledFuture<*>? = null
    private var session: TwitchSession? = null
    @Volatile private var stopping = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        TwitchNotifications.createChannels(this)
        pollExecutor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "twitch-go-live-poll").apply { isDaemon = true }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishMonitoring("Monitoring stopped.")
            return START_NOT_STICKY
        }

        val preferences = statePreferences()
        if (intent == null && !preferences.getBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!TwitchNotifications.notificationsAllowed(this)) {
            finishMonitoring("Enable notifications to start Twitch monitoring.")
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TwitchNotifications.MONITORING_NOTIFICATION_ID,
                    TwitchNotifications.monitoring(this, "Connecting to Twitch…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(
                    TwitchNotifications.MONITORING_NOTIFICATION_ID,
                    TwitchNotifications.monitoring(this, "Connecting to Twitch…"),
                )
            }
            foregroundStarted = true
        } catch (exception: Exception) {
            finishMonitoring("Could not start background monitoring. Open the app and try again.")
            return START_NOT_STICKY
        }

        stopping = false
        preferences.edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, true)
            .putBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
            .putLong(TwitchAppState.KEY_LAST_SERVICE_HEARTBEAT_MILLIS, System.currentTimeMillis())
            .apply()
        TwitchMonitoringWork.scheduleFallback(this)
        TwitchMonitoringWork.cancelResumeReminder(this)
        TwitchNotifications.cancelResumeReminder(this)
        if (pollTask?.isDone != false) {
            pollTask = pollExecutor.scheduleWithFixedDelay(
                ::pollSafely,
                0,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS,
            )
        }
        return START_STICKY
    }

    private fun pollSafely() {
        if (stopping || !TwitchMonitorPollGuard.tryAcquire()) return
        try {
            statePreferences().edit()
                .putLong(TwitchAppState.KEY_LAST_SERVICE_HEARTBEAT_MILLIS, System.currentTimeMillis())
                .apply()
            if (!TwitchNotifications.notificationsAllowed(this)) {
                finishMonitoring("Notifications are disabled. Enable notifications to resume Twitch monitoring.")
                return
            }

            try {
                when (val outcome = monitorPoller.poll(session)) {
                    is TwitchMonitorPollOutcome.Stop -> {
                        TwitchAccountStateGuard.acquire()
                        try {
                            if (monitorPoller.isCurrentPollToken()) finishMonitoring(outcome.status)
                        } finally {
                            TwitchAccountStateGuard.release()
                        }
                    }
                    is TwitchMonitorPollOutcome.Success -> {
                        TwitchAccountStateGuard.acquire()
                        try {
                            if (stopping || !monitorPoller.isCurrentSession(outcome.session)) return
                            session = outcome.session
                            for (stream in outcome.newlyLiveStreams) {
                                if (stopping) return
                                deliver(stream)
                            }
                            if (stopping) return
                            TwitchNotifications.postLiveStreams(
                                this,
                                outcome.newlyLiveStreams,
                                outcome.session,
                            )
                            monitorPoller.commit(outcome)

                            val status = if (outcome.streamCount == 0) {
                                "Monitoring every minute. No followed channels are live."
                            } else {
                                "Monitoring every minute. ${outcome.streamCount} followed channel(s) are live."
                            }
                            updateStatus(status)
                        } finally {
                            TwitchAccountStateGuard.release()
                        }
                    }
                }
            } catch (exception: TwitchApiException) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!monitorPoller.isCurrentPollToken()) return
                    when (exception.kind) {
                        TwitchApiException.Kind.INVALID_TOKEN ->
                            finishMonitoring("OAuth token is invalid or expired. Replace it to resume.")
                        TwitchApiException.Kind.MISSING_SCOPE ->
                            finishMonitoring("OAuth token needs the user:read:follows scope.")
                        else ->
                            updateStatus("${exception.message ?: "Twitch request failed."} Retrying in about one minute.")
                    }
                } finally {
                    TwitchAccountStateGuard.release()
                }
            } catch (exception: TokenVaultException) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!monitorPoller.isCurrentPollToken()) return
                    finishMonitoring(exception.message ?: "The saved token could not be read. Enter it again.")
                } finally {
                    TwitchAccountStateGuard.release()
                }
            } catch (exception: Exception) {
                TwitchAccountStateGuard.acquire()
                try {
                    if (!monitorPoller.isCurrentPollToken()) return
                    updateStatus("Unexpected error while checking Twitch. Retrying in about one minute.")
                } finally {
                    TwitchAccountStateGuard.release()
                }
            }
        } finally {
            TwitchMonitorPollGuard.release()
        }
    }

    private fun deliver(stream: TwitchStream) {
        if (!TwitchNotifications.isStreamerEnabled(this, stream.userId)) return
        if (AppVisibility.isActivityVisible) {
            mainHandler.post {
                if (!TwitchNotifications.isStreamerEnabled(this, stream.userId)) return@post
                Toast.makeText(this, "${stream.displayName} is live", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatus(status: String) {
        statePreferences().edit().putString(TwitchAppState.KEY_STATUS, status).apply()
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, status),
        )
        if (foregroundStarted && !stopping) {
            getSystemService(android.app.NotificationManager::class.java).notify(
                TwitchNotifications.MONITORING_NOTIFICATION_ID,
                TwitchNotifications.monitoring(this, status),
            )
        }
    }

    private fun finishMonitoring(status: String, resumeAfterTimeout: Boolean = false) {
        if (stopping) return
        stopping = true
        pollTask?.cancel(true)
        pollTask = null
        session = null
        statePreferences().edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, resumeAfterTimeout)
            .putBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, resumeAfterTimeout)
            .putString(TwitchAppState.KEY_STATUS, status)
            .apply()
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, status),
        )
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        if (!resumeAfterTimeout) TwitchMonitoringWork.cancelFallback(this)
        TwitchMonitoringWork.cancelResumeReminder(this)
        TwitchNotifications.cancelResumeReminder(this)
        stopSelf()
    }

    private fun statePreferences() =
        getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)

    override fun onTimeout(startId: Int, fgsType: Int) {
        finishMonitoring(
            TwitchAppState.STATUS_BACKGROUND_FALLBACK,
            resumeAfterTimeout = true,
        )
        TwitchMonitoringWork.scheduleFallback(this)
        TwitchMonitoringWork.scheduleResumeReminder(this)
    }

    override fun onDestroy() {
        stopping = true
        pollTask?.cancel(true)
        pollExecutor.shutdownNow()
        session = null
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.lycaennaa.twitchalerts.STOP_MONITORING"
        const val ACTION_STATUS = "com.lycaennaa.twitchalerts.MONITOR_STATUS"
        const val EXTRA_STATUS = "status"
        private const val POLL_INTERVAL_SECONDS = 60L
    }
}
