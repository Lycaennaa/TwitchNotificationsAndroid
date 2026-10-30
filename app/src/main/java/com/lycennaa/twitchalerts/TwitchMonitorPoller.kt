package com.lycaennaa.twitchalerts

import android.content.Context
import java.util.LinkedHashSet
import java.util.concurrent.locks.ReentrantLock

internal sealed interface TwitchMonitorPollOutcome {
    data class Success(
        val session: TwitchSession,
        val streamCount: Int,
        val newlyLiveStreams: List<TwitchStream>,
        val currentLiveStreamIds: Set<String>,
    ) : TwitchMonitorPollOutcome

    data class Stop(val status: String) : TwitchMonitorPollOutcome
}

internal class TwitchMonitorPoller(context: Context) {
    private val applicationContext = context.applicationContext
    private val apiClient = TwitchApiClient()
    private var tokenSnapshot: String? = null

    fun poll(session: TwitchSession?): TwitchMonitorPollOutcome {
        tokenSnapshot = null
        val token = TokenVault(applicationContext).read()
        tokenSnapshot = token
        if (token.isNullOrBlank()) {
            return TwitchMonitorPollOutcome.Stop("No saved OAuth token. Enter a token to start monitoring.")
        }

        val activeSession = session?.takeIf { it.accessToken == token }
            ?: apiClient.authenticate(token)
        val currentStreams = apiClient.followedStreams(activeSession)
        val preferences = preferences()
        val baselineMatchesAccount =
            preferences.getBoolean(TwitchAppState.KEY_BASELINE_INITIALIZED, false) &&
                preferences.getString(TwitchAppState.KEY_BASELINE_USER_ID, null) == activeSession.userId
        val previousIds: Set<String>? = if (baselineMatchesAccount) {
            preferences.getStringSet(TwitchAppState.KEY_LIVE_STREAM_IDS, emptySet<String>())
                ?.toSet() ?: emptySet()
        } else {
            null
        }
        val newlyLiveStreams = TwitchNotificationState.newlyLiveStreams(currentStreams, previousIds)
        val currentLiveStreamIds = currentStreams.mapTo(LinkedHashSet()) { it.id }

        return TwitchMonitorPollOutcome.Success(
            session = activeSession,
            streamCount = currentStreams.size,
            newlyLiveStreams = newlyLiveStreams,
            currentLiveStreamIds = currentLiveStreamIds,
        )
    }

    fun commit(outcome: TwitchMonitorPollOutcome.Success) {
        preferences().edit()
            .putBoolean(TwitchAppState.KEY_BASELINE_INITIALIZED, true)
            .putString(TwitchAppState.KEY_BASELINE_USER_ID, outcome.session.userId)
            .putStringSet(TwitchAppState.KEY_LIVE_STREAM_IDS, outcome.currentLiveStreamIds)
            .apply()
    }

    fun isCurrentSession(session: TwitchSession): Boolean {
        val currentToken = try {
            TokenVault(applicationContext).read()
        } catch (exception: TokenVaultException) {
            return false
        }
        return matchesTokenSnapshot(session.accessToken, currentToken)
    }

    fun isCurrentPollToken(): Boolean {
        val currentToken = try {
            TokenVault(applicationContext).read()
        } catch (exception: TokenVaultException) {
            return tokenSnapshot == null
        }
        return matchesTokenSnapshot(tokenSnapshot, currentToken)
    }
    private fun preferences() =
        applicationContext.getSharedPreferences(TwitchAppState.PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal object TwitchMonitorPollGuard {
    private val lock = ReentrantLock()

    fun tryAcquire(): Boolean = lock.tryLock()

    fun release() = lock.unlock()
}

internal fun matchesTokenSnapshot(pollToken: String?, currentToken: String?): Boolean =
    pollToken == currentToken
