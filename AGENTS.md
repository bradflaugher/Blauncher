# Agent and contributor instructions

Blauncher is a minimal, gesture-driven Android launcher — a personal hard
fork of Olauncher. Read `README.md` for the feature overview, `GUIDE.md` for
how the UI behaves, and `SECURITY.md` for the privacy/security design notes.
Keep all three in sync with any behavior you change.

## Latest-only platform policy (non-negotiable)

This project builds with **only the latest public stable version of
everything** and never carries code just for older devices:

- `targetSdk` and `compileSdk` are always the **latest public stable Android
  API level** (`app/build.gradle`). When a new stable Android version ships,
  bump them together.
- `minSdk` is the newest API the code actually uses (today Android 15,
  API 35, for Private Space), so older phones that happen to support
  everything come free. If a feature needs a newer API, raise `minSdk` to
  it rather than add a check.
- No backwards-compatibility code: no `Build.VERSION.SDK_INT` checks, no
  `*Compat` shims kept solely for older devices, no legacy code paths.
  Delete such code on sight instead of extending it. Lint's `NewApi` error
  keeps the code honest about `minSdk`.
- The toolchain tracks the latest stable releases too: Android Gradle Plugin
  and all dependencies in `gradle/libs.versions.toml`, Gradle in
  `gradle/wrapper/gradle-wrapper.properties` (keep the distribution
  checksum-pinned), and the JDK in `app/build.gradle`. Dependabot keeps
  these current — do not hold versions back for compatibility reasons.
- If a change only works by targeting an older API level or downgrading a
  dependency, that change is wrong for this project.

Do not copy version numbers into docs. Pins live in Gradle and are updated
by Dependabot.

## Security and privacy invariants

Never regress these without an explicit request from the maintainer:

- **No `INTERNET` permission.** The app never touches the network; web
  searches are handed to other apps via intents.
- **No `QUERY_ALL_PACKAGES`.** Package visibility comes only from the
  `<queries>` intents in the manifest (launchable apps, the home app, the
  default browser and web search). Add an intent there rather than the
  permission.
- **No usage-stats access.** Smart ordering learns only from launches made
  inside the launcher and stores its data in local app preferences.
- **No backups of app data.** Auto-backup and data-extraction rules stay
  empty so nothing (including learned weights) leaves the device.
- **No accounts, sync, analytics, accessibility service, or
  launcher-managed wallpaper.**
- CI actions stay pinned to commit SHAs; CodeQL scans Kotlin and workflows.

## Build and test

Toolchain versions live in Gradle — `app/build.gradle`,
`gradle/libs.versions.toml`, and
`gradle/wrapper/gradle-wrapper.properties`.

```sh
./gradlew lint test assembleDebug   # what CI runs on every PR (plus assembleRelease)
```

Run this before pushing. Unit tests live in `app/src/test/`.

## Conventions

- The application ID is `com.bradflaugher.blauncher`; the inherited source
  namespace stays `app.olauncher` — do not rename packages.
- Release versioning comes from `BLAUNCHER_VERSION_CODE` /
  `BLAUNCHER_VERSION_NAME`; release signing uses the four `BLAUNCHER_*`
  signing variables and is all-or-nothing (see `README.md`).
- Every push to `main` publishes the signed APK to the single `latest`
  GitHub release with a SHA-256 checksum. Keep `main` green.
- License is GPL-3.0; keep the Olauncher attribution intact.
