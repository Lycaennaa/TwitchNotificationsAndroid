# Architecture

## Overview

Twitch Live Alerts is a single-activity Android app. It accepts a Twitch OAuth access token, reads the user's followed channels and live streams from Twitch, and posts local Android notifications for newly live streams.

## Components

- `MainActivity` owns token entry, notification-permission requests, monitoring controls, followed-streamer toggles, and status display.
- `TokenVault` stores the OAuth token encrypted with AES-GCM in app-private preferences; its key is managed by Android Keystore.
- `TwitchApiClient` validates the token and requests followed streams, followed channels, Twitch user details, and profile-image URLs over HTTPS.
- `TwitchMonitorService` runs as a user-started foreground service and schedules a poll every 60 seconds while active.
- `TwitchMonitorPoller` reads the current token, fetches streams, compares stream IDs with the saved baseline, and commits the new baseline.
- `TwitchNotifications` owns the monitoring, test, and per-streamer notification channels and posts live-stream alerts.
- `TwitchMonitoringWork` schedules a 15-minute WorkManager fallback and a resume reminder when the foreground service is no longer active.
- `TwitchAvatarLoader` loads profile images only from Twitch's `static-cdn.jtvnw.net` host.

## Key flows

1. The user pastes a token and starts monitoring. The app validates it with Twitch and requires `user:read:follows`.
2. The poller retrieves followed live streams. A first poll, or a poll after the Twitch account changes, records a baseline without sending alerts for already-live streams.
3. Later polls compare live stream IDs. New IDs are sent to the notification layer unless that streamer's alerts are disabled.
4. If Android stops or times out the foreground service, WorkManager may run a best-effort fallback. The optional reboot setting is honored only when the user enabled it and monitoring had been requested.

## Constraints and decisions

- The normal foreground-service polling interval is 60 seconds. WorkManager's 15-minute interval is best effort, not a real-time or continuous-delivery guarantee; Android may delay or stop work.
- The app targets Android API 34, compiles against API 36, and supports API 26 and later.
- The manifest disables cleartext traffic and Android app-data backup. The token is encrypted at rest; other app state is stored in private preferences.
- See [Privacy](PRIVACY.md) for data flows and [Security](SECURITY.md) for safe-use limits.
