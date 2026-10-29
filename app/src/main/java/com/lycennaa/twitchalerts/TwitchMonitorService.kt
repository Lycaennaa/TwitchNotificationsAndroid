package com.lycaennaa.twitchalerts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.widget.Toast
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal object TwitchAppState {
    const val PREFERENCES_NAME = "twitch_monitor_state"
    const val KEY_STATUS = "status"
    const val KEY_MONITORING_REQUESTED = "monitoring_requested"
    const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
    const val KEY_BASELINE_USER_ID = "baseline_user_id"
    const val KEY_LIVE_STREAM_IDS = "live_stream_ids"
}

class TwitchMonitorService : Service() {
    private val apiClient = TwitchApiClient()
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
            .apply()
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
        if (stopping) return
        if (!TwitchNotifications.notificationsAllowed(this)) {
            finishMonitoring("Notifications are disabled. Enable them to resume Twitch monitoring.")
            return
        }

        try {
            val token = TokenVault(this).read()
            if (token.isNullOrBlank()) {
                finishMonitoring("No saved OAuth token. Enter a token to start monitoring.")
                return
            }
            val activeSession = session?.takeIf { it.accessToken == token }
                ?: apiClient.authenticate(token).also { session = it }
            val currentStreams = apiClient.followedStreams(activeSession)
            if (stopping) return

            val preferences = statePreferences()
            val baselineMatchesAccount =
                preferences.getBoolean(TwitchAppState.KEY_BASELINE_INITIALIZED, false) &&
                    preferences.getString(TwitchAppState.KEY_BASELINE_USER_ID, null) == activeSession.userId
            val previousIds: Set<String>? = if (baselineMatchesAccount) {
                preferences.getStringSet(TwitchAppState.KEY_LIVE_STREAM_IDS, emptySet<String>())
                    ?.toSet() ?: emptySet()
            } else {
                null
            }
            val newlyLive = TwitchNotificationState.newlyLiveStreams(currentStreams, previousIds)
            for (stream in newlyLive) {
                if (stopping) return
                deliver(stream)
            }

            val currentIds = currentStreams.mapTo(LinkedHashSet()) { it.id }
            preferences.edit()
                .putBoolean(TwitchAppState.KEY_BASELINE_INITIALIZED, true)
                .putString(TwitchAppState.KEY_BASELINE_USER_ID, activeSession.userId)
                .putStringSet(TwitchAppState.KEY_LIVE_STREAM_IDS, currentIds)
                .apply()

            val status = if (currentStreams.isEmpty()) {
                "Monitoring every minute. No followed channels are live."
            } else {
                "Monitoring every minute. ${currentStreams.size} followed channel(s) are live."
            }
            updateStatus(status)
        } catch (exception: TwitchApiException) {
            when (exception.kind) {
                TwitchApiException.Kind.INVALID_TOKEN ->
                    finishMonitoring("OAuth token is invalid or expired. Replace it to resume.")
                TwitchApiException.Kind.MISSING_SCOPE ->
                    finishMonitoring("OAuth token needs the user:read:follows scope.")
                else ->
                    updateStatus("${exception.message ?: "Twitch request failed."} Retrying in about one minute.")
            }
        } catch (exception: TokenVaultException) {
            finishMonitoring(exception.message ?: "The saved token could not be read. Enter it again.")
        } catch (exception: Exception) {
            updateStatus("Unexpected error while checking Twitch. Retrying in about one minute.")
        }
    }

    private fun deliver(stream: TwitchStream) {
        if (AppVisibility.isActivityVisible) {
            mainHandler.post {
                Toast.makeText(this, "${stream.displayName} is live", Toast.LENGTH_LONG).show()
            }
        }
        TwitchNotifications.postLiveStream(this, stream)
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

    private fun finishMonitoring(status: String) {
        if (stopping) return
        stopping = true
        pollTask?.cancel(true)
        pollTask = null
        session = null
        statePreferences().edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
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
        stopSelf()
    }

    private fun statePreferences() =
        getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)

    override fun onTimeout(startId: Int, fgsType: Int) {
        finishMonitoring(
            "Android stopped monitoring after its foreground-service time limit. Open the app to resume.",
        )
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
