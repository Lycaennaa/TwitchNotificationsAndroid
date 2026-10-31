# Changelog

## Unreleased
- Emit debug and release APKs with app name, version, and build type, without Gradle's default APK names.

## 1.0.2

- Use streamer profile pictures as live notification status-bar icons.
- Show underlying connection errors when Twitch cannot be reached.
- Add delayed WorkManager checks and an app-resume reminder after Android stops foreground monitoring.
- Target API 34 for sideloading to avoid Android 15's API 35+ `dataSync` timeout.
- Add an opt-in reboot toggle that resumes previously requested monitoring after `BOOT_COMPLETED`.
- Run a heartbeat-gated WorkManager watchdog for best-effort polling after unexpected service stops.
- Resume reminders work when tapped with the app already open; watchdog results survive foreground recovery and stale account results are discarded.

## 1.0.1

- Request standard Android Toasts for go-live alerts while the app is backgrounded.

## 1.0.0

- Add an AMOLED-black Android app for Twitch followed-channel go-live alerts.
- Validate and encrypt a pasted `user:read:follows` OAuth token.
- Poll Twitch in a user-started foreground service and deliver native Android notifications, with foreground Toasts.
