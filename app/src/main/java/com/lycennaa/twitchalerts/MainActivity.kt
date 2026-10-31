package com.lycaennaa.twitchalerts

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import android.widget.ListView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val apiClient = TwitchApiClient()
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val streamerLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var tokenVault: TokenVault
    private lateinit var tokenInput: EditText
    private lateinit var tokenStateLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var streamerAlertStatusLabel: TextView
    private lateinit var streamerAlertList: ListView
    @Volatile
    private var streamerLoadGeneration = 0
    private lateinit var streamerChannelAdapter: ArrayAdapter<TwitchFollowedChannel>
    private var activeStreamerLoadId: Int? = null
    private var streamerLoadRequestedAgain = false
    private var pendingPermissionAction: (() -> Unit)? = null
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TwitchMonitorService.ACTION_STATUS) {
                intent.getStringExtra(TwitchMonitorService.EXTRA_STATUS)?.let(::showStatus)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenVault = TokenVault(this)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(30), dp(24), dp(32))
            setBackgroundColor(Color.BLACK)
        }
        addText(content, "TWITCH LIVE ALERTS", 24f, Color.WHITE, bold = true, topMargin = 0)
        addText(
            content,
            "AMOLED black · followed channels only",
            14f,
            Color.LTGRAY,
            topMargin = 8,
        )
        addText(
            content,
            "Paste a Twitch user access token with the user:read:follows permission. The token is validated before it is encrypted and saved on this device.",
            14f,
            Color.LTGRAY,
            topMargin = 24,
        )
        addText(content, "OAuth access token", 16f, Color.WHITE, bold = true, topMargin = 22)

        tokenInput = EditText(this).apply {
            hint = "Paste token (oauth: prefix optional)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(145, 71, 255))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(
            tokenInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        tokenStateLabel = addText(content, "No token saved.", 13f, Color.LTGRAY, topMargin = 8)

        addButton(content, "Save token & start monitoring", topMargin = 20) {
            saveTokenAndStart()
        }
        addButton(content, "Start with saved token", topMargin = 8) {
            startWithSavedToken()
        }

        statusLabel = addText(content, "Monitoring is stopped.", 14f, Color.WHITE, topMargin = 18)
        addText(
            content,
            "Background checks use a user-started foreground service and require the ongoing notification. Android may delay or stop background work; see README for platform limits.",
            13f,
            Color.LTGRAY,
            topMargin = 8,
        )

        val bootPreferences = getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)
        val autoStartOnBootSwitch = Switch(this).apply {
            text = "Start monitoring after device restart"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = bootPreferences.getBoolean(TwitchAppState.KEY_AUTO_START_ON_BOOT, false)
            setOnCheckedChangeListener { _, enabled ->
                bootPreferences.edit()
                    .putBoolean(TwitchAppState.KEY_AUTO_START_ON_BOOT, enabled)
                    .apply()
            }
        }
        content.addView(
            autoStartOnBootSwitch,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            },
        )
        addText(
            content,
            "Starts only if monitoring was active before restart; it cannot override Android background limits.",
            13f,
            Color.LTGRAY,
            topMargin = 4,
        )
        addText(content, "Streamer alerts", 18f, Color.WHITE, bold = true, topMargin = 22)
        streamerAlertStatusLabel = addText(
            content,
            "Load followed streamers to choose which alerts are allowed.",
            13f,
            Color.LTGRAY,
            topMargin = 6,
        )
        addButton(content, "Refresh followed streamers", topMargin = 8) {
            loadFollowedChannels()
        }
        streamerChannelAdapter = object : ArrayAdapter<TwitchFollowedChannel>(this, 0) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val channel = getItem(position) ?: return convertView ?: Switch(this@MainActivity)
                val toggle = (convertView as? Switch) ?: Switch(this@MainActivity)
                toggle.setOnCheckedChangeListener(null)
                toggle.text = if (channel.userLogin.isBlank()) {
                    channel.displayName
                } else {
                    "${channel.displayName} (@${channel.userLogin})"
                }
                toggle.setTextColor(Color.WHITE)
                toggle.textSize = 14f
                toggle.isChecked = TwitchNotifications.isStreamerEnabled(this@MainActivity, channel.userId)
                toggle.setOnCheckedChangeListener { _, enabled ->
                    TwitchNotifications.setStreamerEnabled(this@MainActivity, channel.userId, enabled)
                }
                return toggle
            }
        }
        streamerAlertList = ListView(this).apply {
            adapter = streamerChannelAdapter
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        content.addView(
            streamerAlertList,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360),
            ).apply { topMargin = dp(4) },
        )

        addButton(content, "Send test notification", topMargin = 22) {
            requireNotificationPermission {
                TwitchNotifications.createChannels(this)
                try {
                    TwitchNotifications.postTest(this)
                    Toast.makeText(this, "Test alert sent", Toast.LENGTH_SHORT).show()
                } catch (exception: Exception) {
                    showStatus("Could not send a test notification. Check Android notification settings.")
                }
            }
        }
        addButton(content, "Stop monitoring", topMargin = 8) {
            stopMonitoring()
        }
        addButton(content, "Forget token", topMargin = 8) {
            forgetToken()
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                val safeInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    intArrayOf(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                }
                view.setPadding(
                    dp(8) + safeInsets[0],
                    safeInsets[1],
                    dp(8) + safeInsets[2],
                    safeInsets[3],
                )
                insets
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        loadFollowedChannels()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isActivityVisible = true
        val filter = IntentFilter(TwitchMonitorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        refreshTokenState()
        val preferences = getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)
        val status = preferences.getString(TwitchAppState.KEY_STATUS, null)
        showStatus(status ?: "Monitoring is stopped.")
        resumeMonitoringIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeMonitoringIfNeeded()
    }

    private fun resumeMonitoringIfNeeded() {
        val preferences = getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)
        if (
            TwitchAppState.shouldResumeAfterTimeout(
                monitoringRequested = preferences.getBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false),
                resumeAfterTimeout = preferences.getBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false),
            )
        ) {
            startMonitoringService()
        }
    }

    override fun onStop() {
        AppVisibility.isActivityVisible = false
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        streamerLoadRequestedAgain = false
        backgroundExecutor.shutdownNow()
        streamerLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            action?.invoke()
        } else {
            showStatus("Notification permission is required for go-live alerts and background monitoring.")
        }
    }

    private fun saveTokenAndStart() {
        val token = TwitchApiClient.normalizeToken(tokenInput.text?.toString().orEmpty())
        if (token.isBlank()) {
            startWithSavedToken()
            return
        }
        requireNotificationPermission {
            showStatus("Validating the OAuth token with Twitch…")
            backgroundExecutor.execute {
                try {
                    val session = apiClient.authenticate(token)
                    tokenVault.save(session.accessToken)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        tokenInput.text?.clear()
                        refreshTokenState()
                        startMonitoringService()
                        loadFollowedChannels()
                    }
                } catch (exception: Exception) {
                    val message = when (exception) {
                        is TwitchApiException -> exception.message
                        is TokenVaultException -> exception.message
                        else -> "Could not validate or securely save the token."
                    } ?: "Could not validate the Twitch OAuth token."
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) showStatus(message)
                    }
                }
            }
        }
    }

    private fun startWithSavedToken() {
        requireNotificationPermission {
            try {
                if (tokenVault.read().isNullOrBlank()) {
                    showStatus("Enter and save a Twitch OAuth token first.")
                    refreshTokenState()
                } else {
                    startMonitoringService()
                }
            } catch (exception: TokenVaultException) {
                showStatus(exception.message ?: "The saved token could not be read. Enter it again.")
            }
        }
    }

    private fun startMonitoringService() {
        val preferences = getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE)
        val resumeAfterTimeout =
            preferences.getBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
        preferences.edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, true)
            .putString(TwitchAppState.KEY_STATUS, "Starting Twitch monitoring…")
            .apply()
        showStatus("Starting Twitch monitoring…")
        val intent = Intent(this, TwitchMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (exception: Exception) {
            val message = "Android could not start background monitoring. Keep the app open and try again."
            preferences.edit()
                .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, resumeAfterTimeout)
                .putString(TwitchAppState.KEY_STATUS, message)
                .apply()
            showStatus(message)
        }
    }

    private fun stopMonitoring() {
        val message = "Stopping Twitch monitoring…"
        getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
            .putBoolean(TwitchAppState.KEY_RESUME_AFTER_TIMEOUT, false)
            .apply()
        TwitchMonitoringWork.cancelFallback(this)
        TwitchMonitoringWork.cancelResumeReminder(this)
        TwitchNotifications.cancelResumeReminder(this)
        showStatus(message)
        try {
            startService(
                Intent(this, TwitchMonitorService::class.java)
                    .setAction(TwitchMonitorService.ACTION_STOP),
            )
        } catch (exception: Exception) {
            showStatus("Monitoring stopped.")
        }
    }

    private fun forgetToken() {
        stopMonitoring()
        try {
            tokenVault.clear()
            refreshTokenState()
            loadFollowedChannels()
            showStatus("Saved OAuth token removed.")
        } catch (exception: TokenVaultException) {
            showStatus(exception.message ?: "Could not remove the saved token.")
        }
    }

    private fun requireNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissionAction = action
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
            return
        }
        if (!TwitchNotifications.notificationsAllowed(this)) {
            showStatus("Enable notifications for Twitch Live Alerts in Android Settings to continue.")
            return
        }
        action()
    }

    private fun refreshTokenState() {
        tokenStateLabel.text = try {
            if (tokenVault.read() == null) "No token saved." else "OAuth token saved securely on this device."
        } catch (exception: TokenVaultException) {
            "Saved token unavailable. Enter it again."
        }
    }

    private fun showStatus(status: String) {
        statusLabel.text = status
    }
    private fun loadFollowedChannels() {
        val requestId = ++streamerLoadGeneration
        val token = try {
            tokenVault.read()
        } catch (_: TokenVaultException) {
            streamerLoadRequestedAgain = false
            streamerChannelAdapter.clear()
            streamerAlertStatusLabel.text = "Saved token unavailable. Enter it again to manage alerts."
            return
        }
        if (token.isNullOrBlank()) {
            streamerLoadRequestedAgain = false
            streamerChannelAdapter.clear()
            streamerAlertStatusLabel.text = "Save an OAuth token to manage streamer alerts."
            return
        }
        if (activeStreamerLoadId != null) {
            streamerLoadRequestedAgain = true
            streamerAlertStatusLabel.text = "Refreshing followed streamers…"
            return
        }

        activeStreamerLoadId = requestId
        streamerAlertStatusLabel.text = "Loading followed streamers…"
        streamerLoadExecutor.execute {
            try {
                val session = apiClient.authenticate(token)
                val channels = apiClient.followedChannels(session)
                if (requestId == streamerLoadGeneration && tokenVault.read() == token) {
                    TwitchNotifications.createStreamerChannels(applicationContext, channels)
                }
                runOnUiThread {
                    if (isCurrentStreamerLoad(requestId, token)) {
                        streamerChannelAdapter.setNotifyOnChange(false)
                        streamerChannelAdapter.clear()
                        streamerChannelAdapter.addAll(channels)
                        streamerChannelAdapter.notifyDataSetChanged()
                        streamerAlertStatusLabel.text = if (channels.isEmpty()) {
                            "No followed streamers found."
                        } else {
                            "${channels.size} followed streamer(s). Alerts are on unless switched off here."
                        }
                    }
                    finishStreamerLoad(requestId)
                }
            } catch (exception: Exception) {
                val message = when (exception) {
                    is TwitchApiException -> exception.message
                    else -> "Could not load followed streamers."
                } ?: "Could not load followed streamers."
                runOnUiThread {
                    if (isCurrentStreamerLoad(requestId, token)) {
                        streamerAlertStatusLabel.text = message
                    }
                    finishStreamerLoad(requestId)
                }
            }
        }
    }

    private fun isCurrentStreamerLoad(requestId: Int, token: String): Boolean {
        if (requestId != streamerLoadGeneration || isFinishing || isDestroyed) return false
        return try {
            tokenVault.read() == token
        } catch (_: TokenVaultException) {
            false
        }
    }

    private fun finishStreamerLoad(requestId: Int) {
        if (activeStreamerLoadId != requestId) return
        activeStreamerLoadId = null
        if (isFinishing || isDestroyed) {
            streamerLoadRequestedAgain = false
            return
        }
        if (streamerLoadRequestedAgain) {
            streamerLoadRequestedAgain = false
            loadFollowedChannels()
        }
    }


    private fun addText(
        parent: LinearLayout,
        text: String,
        textSize: Float,
        textColor: Int,
        bold: Boolean = false,
        topMargin: Int,
    ): TextView {
        val view = TextView(this).apply {
            this.text = text
            setTextSize(textSize)
            setTextColor(textColor)
            gravity = Gravity.START
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        parent.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { this.topMargin = dp(topMargin) },
        )
        return view
    }

    private fun addButton(parent: LinearLayout, title: String, topMargin: Int, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { this.topMargin = dp(topMargin) },
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 78
    }
}
