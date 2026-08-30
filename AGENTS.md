# Agent and contributor instructions

Blauncher is a text-only, gesture-driven Android launcher — a personal hard
fork of Olauncher. Read `README.md` for the feature overview, `GUIDE.md` for
how the UI behaves, and `SECURITY.md` for the privacy/security design notes.
Keep all three in sync with any behavior you change.

## Latest-only platform policy (non-negotiable)

This project always supports **only the latest public stable version of
everything** — maximum security and freshness, zero backwards compatibility:

- `minSdk`, `targetSdk`, and `compileSdk` are always the **latest public
  stable Android API level**, all three set to the same value
  (`app/build.gradle`). When a new stable Android version ships, bump all
  three together; do not leave a lower `minSdk` behind.
- No backwards-compatibility code: no `Build.VERSION.SDK_INT` checks for
  older releases, no `*Compat` shims kept solely for pre-latest devices, no
  legacy code paths, no support for previous Android versions. Delete such
  code on sight instead of extending it.
- The toolchain tracks the latest stable releases too: Android Gradle Plugin
  and all dependencies in `gradle/libs.versions.toml`, Gradle in
  `gradle/wrapper/gradle-wrapper.properties` (keep the distribution
  checksum-pinned), and the JDK named in `README.md` and CI. Dependabot keeps
  these current — do not hold versions back for compatibility reasons.
- If a change only works by targeting an older API level or downgrading a
  dependency, that change is wrong for this project.

## Security and privacy invariants

Never regress these without an explicit request from the maintainer:

- **No `INTERNET` permission.** The app never touches the network; web
  searches are handed to other apps via intents.
- **No usage-stats access.** Smart ordering learns only from launches made
  inside the launcher and stores its data in local app preferences.
- **No backups of app data.** Auto-backup and data-extraction rules stay
  empty so nothing (including learned weights) leaves the device.
- **No accounts, sync, analytics, accessibility service, or
  launcher-managed wallpaper.**
- CI actions stay pinned to commit SHAs; CodeQL scans Kotlin and workflows.

## Build and test

JDK 17 and the latest Android SDK platform (currently
`platforms;android-37.0`) are required.

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
