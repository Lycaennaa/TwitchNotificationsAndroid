# Setup

This guide covers installing and using the app. Source builds are documented in [Development](DEVELOPMENT.md).

## Requirements

- Android 8.0 (API 26) or later.
- Internet access to Twitch.
- A Twitch OAuth access token with the `user:read:follows` scope.
- Notification permission on Android 13 (API 33) and later.

## Install

This repository does not specify a public download URL. Install the signed APK supplied by the project distributor, or build an APK using [Development](DEVELOPMENT.md). Install APKs only from a source you trust; Android may ask you to allow the installer to install apps from that source.

## Configure

1. Open **Twitch Live Alerts** and grant notification permission when prompted.
2. Obtain an OAuth access token through a trusted Twitch authorization flow. Grant only the `user:read:follows` scope needed by the app. Treat the token as a password; do not paste it into third-party token tools or send it to anyone.
3. Paste the token into the app. An `oauth:` prefix is optional.
4. Choose **Save token & start monitoring** to validate and save the token, or **Start with saved token** if you already saved one.

## Verify

Choose **Send test notification**. A test alert should appear if Android notifications are allowed for the app. If it does not, check the app's notification permission and Android notification settings.

## Use

- Refresh the followed-streamer list, then switch off alerts for any streamer you do not want to notify you about. Alerts are enabled by default.
- Keep monitoring active using the app's foreground service. The service polls every minute while active; Android may delay or stop background work, and the WorkManager fallback is best effort.
- Enable **Start monitoring after device restart** only if you want the app to resume after reboot. The setting is opt-in and applies only when monitoring was previously requested.
- Choose **Stop monitoring** to stop checks. Choose **Forget token** to remove the saved token from the app.

See [Privacy](PRIVACY.md) for data handling, [Security](SECURITY.md) for token safety, and [Support](SUPPORT.md) if you need help.
