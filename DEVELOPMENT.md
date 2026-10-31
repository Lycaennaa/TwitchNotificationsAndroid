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
