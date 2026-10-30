# Twitch Live Alerts for Android

A small Kotlin Android app for Twitch followed-channel go-live alerts. It uses a black AMOLED interface, lets you paste a Twitch user OAuth access token, and validates that token for the `user:read:follows` scope before saving it.

## Use

1. Install and open the app.
2. Paste a user access token with `user:read:follows`, then tap **Save token & start monitoring**. The token is encrypted with Android Keystore and is not displayed again after saving.
3. Allow notifications when Android asks. The app creates a low-priority ongoing notification while monitoring and a separate alert notification channel for streams going live.
4. Tap **Stop monitoring** to stop polling, or **Forget token** to stop and erase the saved token.

Go-live events post a native Android notification whether the app is open or in the background; when the app is open, they also show a Toast. Streams already live on the first successful poll are treated as the initial baseline and do not trigger alerts.

Go-live notifications use Android's high-importance channel and attach the streamer's Twitch profile image when available. Heads-up and lock-screen presentation remain subject to Android and user settings; these one-way alerts are not Android conversation or bubble notifications.

## Background behavior

Monitoring uses a user-started foreground service so it can continue polling after the activity is closed. It waits about 60 seconds after each completed poll; network time and Android scheduling mean this is best-effort, not an exact one-minute guarantee. The ongoing notification is required. Enable **Start monitoring after device restart** to attempt resuming after a normal reboot, but only if monitoring was still requested. A force-stop or device restrictions may prevent automatic start; open the app to start again.

This sideload build targets API 34 (Android 14). Android 15 and later apply the six-hour `dataSync` timeout to apps targeting API 35 or higher, so API 34 avoids that specific timeout.
A WorkManager watchdog runs while monitoring is requested. It skips checks while the foreground-service heartbeat is fresh and polls Twitch if it goes stale. Checks are best-effort at 15-minute intervals or later; Android may defer them, and short streams may be missed.
After a timeout or stale heartbeat, a separate reminder asks you to reopen the app; Android may defer it. Opening the app requests one-minute monitoring again. This still does not guarantee 24/7 operation because Android or device vendors can stop services.
Google Play requires API 36 for new apps and updates as of August 31, 2026. This API 34 build is sideload-only.

## Build

Requires JDK 17 and Android SDK Platform 36. The Gradle wrapper downloads Gradle 9.3.1 on first use.

Build a debug APK:

```sh
./gradlew :app:assembleDebug
```

Build the signed release APK:

```sh
./gradlew :app:assembleRelease
```

The APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

Release signing uses `signing.properties` and `app/signing/twitch-alerts-upload.p12`. Both are excluded from version control; back them up securely because losing the keystore prevents signing updates for this app.
