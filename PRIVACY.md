# Privacy

Twitch Live Alerts stores the OAuth access token only on the device. It encrypts the token with AES-GCM and a key held by Android Keystore; the app disables Android backup so the encrypted token is not restored without its key. **Forget token** removes the encrypted value and the Keystore key.

The app sends the token over HTTPS to Twitch to validate it at `id.twitch.tv/oauth2/validate` and to fetch followed live streams from `api.twitch.tv/helix/streams/followed`. Twitch returns the client and user IDs used for API requests. The app does not use an app backend or analytics service. Stream names, titles, and game names are displayed in local Android Toasts or notifications.

Monitoring requires an ongoing Android foreground-service notification. Android notification settings control whether go-live alerts appear or play sound. Revoke the OAuth token through Twitch when it is no longer needed, in addition to removing it from the app.
