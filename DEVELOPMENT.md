# Development

This guide is for building, testing, and editing the Android app. End-user installation and use are in [Setup](SETUP.md).

## Requirements

- JDK 17.
- Android SDK platform 36.
- [Just](https://github.com/casey/just#installation), used to run repository commands.
- The checked-in Gradle wrapper uses Gradle 9.3.1. The Android application plugin is 9.1.0.

## Get the source

Check out the repository using the project's hosting service, then run commands from the repository root. `just setup` runs Gradle's help task, bootstrapping the wrapper and confirming that the project can configure. Install Android SDK platform 36 if it is not already available.

## Build

```sh
just build
just build-release
```

`just build` creates the debug APK. `just build-release` assembles the release APK. APKs are named `Twitch Live Alerts - 1.0.2 - DEBUG.apk` and `Twitch Live Alerts - 1.0.2 - RELEASE.apk` under `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/` respectively.

A signed release requires the maintainer-provided signing configuration. Keep signing properties and keystore material out of version control. Without signing credentials, do not treat a release output as a signed distribution build.

## Checks

```sh
just test
just lint
just check
just ci
```

`just check` aggregates unit tests and Android lint. `just ci` runs those checks and assembles the release variant. Use `just dependencies` and `just test-dependencies` when reviewing the resolved third-party dependency graphs.

`just run` installs the debug build on a connected Android device and requires Android Debug Bridge access. `just clean` removes Gradle build outputs and the repository-local `.build` directory. See the [root justfile](justfile) for the exact recipe definitions.

## Generated files

Gradle generates APKs and build metadata under `app/build/`. Do not edit generated APKs or `output-metadata.json` by hand. The Gradle build script names APK outputs; update that source configuration if output naming changes.

See [Contributing](CONTRIBUTING.md) for review and pull-request workflow.

## CI Android signing and releases

`.github/workflows/ci.yml` runs unit tests and debug lint on pull requests and pushes to `master`. The separate `.github/workflows/release-apk.yml` builds and verifies a signed APK when manually dispatched from `master` without publishing, or builds, attests, and publishes on a `v*` tag. The tag must match the APK's `versionName` after removing the leading `v` (for example, `v1.0.2` for `1.0.2`).

Configure these repository Actions secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64` (base64-encoded keystore)
- `ANDROID_SIGNING_KEYSTORE_PASSWORD` (used for the store and key)
- `ANDROID_SIGNING_KEY_ALIAS`

For a first release, create a PKCS12 keystore outside the repository:

```sh
mkdir -p "$HOME/keystores"
keytool -genkeypair -v \
  -keystore "$HOME/keystores/android-release.p12" \
  -storetype PKCS12 \
  -alias android-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android App Signing"
```


At the key password prompt, press Return to reuse the keystore password. This workflow uses that same password for both signing fields.

To convert an existing JKS without changing its signing identity, set `-srcalias` to that keystore's current alias. Replace `YOUR_EXISTING_JKS_ALIAS` below with it; `-destalias` can use the generic `android-release` alias.

```sh
keytool -importkeystore \
  -srckeystore "$HOME/keystores/android-release.jks" \
  -srcstoretype JKS \
  -destkeystore "$HOME/keystores/android-release.p12" \
  -deststoretype PKCS12 \
  -srcalias YOUR_EXISTING_JKS_ALIAS \
  -destalias android-release
```

On macOS, copy the base64-encoded keystore to the clipboard for the `ANDROID_SIGNING_KEYSTORE_BASE64` secret with:

```sh
base64 -i "$HOME/keystores/android-release.p12" | tr -d '\n' | pbcopy
```

Set `ANDROID_SIGNING_KEY_ALIAS` to `android-release`. With PKCS12, use the same password for the store and key; the workflow uses `ANDROID_SIGNING_KEYSTORE_PASSWORD` for both. For local signing, point `signing.properties` at the `.p12` file and set its `storePassword` and `keyPassword` to that same value. Never commit the keystore or `signing.properties`.

Repository secrets are repository-scoped. You can share the same key across repos by configuring the same secrets in each (or using organization secrets where available), but this shares signing identity and compromise risk. Prefer per-app keys unless a shared signing identity is intentional. Keep secure backups of the keystore and passwords.

On a tag-triggered run, the release workflow verifies the APK signature, transfers it to the publish job as a one-day workflow artifact, attaches it to a GitHub Release with generated notes, and records a GitHub build-provenance attestation, verifiable with `gh attestation verify`. Temporary signing files are removed at job completion.
