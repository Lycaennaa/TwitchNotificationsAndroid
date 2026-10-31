# Features

- **Twitch followed-channel alerts.** Validate a user-provided OAuth access token with the `user:read:follows` scope, then read followed channels and live streams from Twitch.
- **Per-streamer alert controls.** Load followed channels and enable or disable notifications for each streamer. Alerts are enabled unless switched off.
- **Go-live notifications.** While monitoring is active, poll for live streams and notify for newly live stream IDs. The initial poll establishes a baseline instead of alerting for streams that were already live.
- **User-started background monitoring.** A foreground service polls every 60 seconds while active. WorkManager provides a best-effort 15-minute fallback; Android can delay or stop background work.
- **Optional restart after reboot.** Monitoring can resume after reboot only when the user enabled the setting and had monitoring requested.
- **Notification controls.** A test notification, a low-importance monitoring notification, and per-streamer high-importance notification channels are provided. Android notification settings can further control delivery and presentation.
- **AMOLED-black interface.** The app uses a black interface and shows token, monitoring, and followed-streamer status.

The app supports Android 8.0 (API 26) and later. See [Setup](SETUP.md) for use and [Privacy](PRIVACY.md) for data handling.
