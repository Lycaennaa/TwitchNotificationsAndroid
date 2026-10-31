# Privacy

Twitch Live Alerts uses the Twitch OAuth access token you provide to load followed channels and live streams. This page describes the app's data handling visible in this repository; Twitch and Android have their own data practices.

## Network requests

- The app validates the token at `https://id.twitch.tv/oauth2/validate`.
- It requests followed streams, followed channels, and Twitch user details from Twitch Helix endpoints over HTTPS. Requests include the access token and Twitch account identifiers.
- Profile images may be downloaded from Twitch's `static-cdn.jtvnw.net` host. The image loader rejects other hosts.

The app's network code targets Twitch's API and static CDN. No separate app-owned backend or analytics SDK is configured in this project. Twitch receives the API requests and handles them under its own policies.

## Local storage

- The OAuth token is encrypted with AES-GCM before being saved in app-private preferences. The encryption key is managed by Android Keystore.
- Monitoring state, the optional boot-resume setting, the live-stream baseline, and per-streamer alert choices are stored in app-private preferences.
- The Android manifest disables cleartext traffic and app-data backup.

The app does not refresh tokens. Use **Forget token** to remove the saved token; Android notification settings control whether notification contents appear on the lock screen. See [Security](SECURITY.md) for safe-use guidance.
