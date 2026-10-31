# Security

## Security-sensitive behavior

The app accepts a Twitch OAuth access token from the user. It sends the token to Twitch's HTTPS token-validation and Helix API endpoints. The token is stored in app-private preferences encrypted with AES-GCM; the key is held in Android Keystore. Other app state, including monitoring preferences and stream baselines, is stored in private preferences.

The Android manifest disables cleartext traffic and app-data backup. The app requires Internet access, notification permission, and a foreground service for monitoring. Boot-time restart is opt-in.

## Safe use

- Use a token from a trusted Twitch authorization flow and grant only `user:read:follows`.
- Treat the token as a secret. Do not put it in issue reports, screenshots, logs, or public messages. If it is exposed, revoke it through Twitch and replace it.
- Use **Forget token** when you no longer want the app to retain the token.
- Review Android notification and lock-screen settings if streamer names or stream titles should remain private.

## Limitations

Encryption at rest does not protect a token while it is entered or used in app memory, or on a compromised device. The app does not refresh OAuth tokens; expired or revoked tokens must be replaced. Android may delay or stop background work, so alerts are not guaranteed to arrive in real time.
