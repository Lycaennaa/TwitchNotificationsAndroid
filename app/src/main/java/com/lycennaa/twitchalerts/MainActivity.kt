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
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val apiClient = TwitchApiClient()
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private lateinit var tokenVault: TokenVault
    private lateinit var tokenInput: EditText
    private lateinit var tokenStateLabel: TextView
    private lateinit var statusLabel: TextView
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
        showStatus(preferences.getString(TwitchAppState.KEY_STATUS, null) ?: "Monitoring is stopped.")
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
        backgroundExecutor.shutdownNow()
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
                .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
                .putString(TwitchAppState.KEY_STATUS, message)
                .apply()
            showStatus(message)
        }
    }

    private fun stopMonitoring() {
        val message = "Stopping Twitch monitoring…"
        getSharedPreferences(TwitchAppState.PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putBoolean(TwitchAppState.KEY_MONITORING_REQUESTED, false)
            .putString(TwitchAppState.KEY_STATUS, message)
            .apply()
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
