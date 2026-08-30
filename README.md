# Blauncher

Blauncher is a personal hard fork of Olauncher. It is maintained for a focused,
private Android launcher setup rather than as an official Olauncher release.

## Focus

- Local app categorization with per-app manual overrides (including multi-group membership so an app can appear in more than one group) and compact markers. Search still dedupes so keyboard matching and single-match auto-launch stay correct.
- Separate **News** and **Media** groups (each with its own glyph), so NYT / WSJ /
  BBC-style apps do not sit under music and video.
- Pinned groups: any number of groups can be pinned to the top of the drawer,
  in an order you choose (AI Agents is pinned first by default).
- Smart group ordering with alphabetical apps inside every group.
- Keyboard-first search with single-match auto-launch and web search fallback.
- A minimal launcher experience without a remote account or synchronization.
- No launcher-managed wallpaper or usage-history access.
- **Android 17 only** (API 37): min, target, and compile SDK are all 37. Built
  with Android Gradle Plugin 9.3 and Gradle 9.7.

Group ordering is driven by a tiny on-device model with nothing to configure.
Smooth built-in time-of-day curves (with weekday and weekend variants) provide
a sensible default — news in the early morning, focus during work hours, media
in the evening — and the launcher quietly learns from the apps you actually
open, bucketed by hour and day type, so your real habits sharpen the order over
time. Learned weights fade with a two-week half-life, live only in local app
preferences, never leave the device, and never touch the system usage-stats
API. Learning can be reset in Settings, and long-pressing an app still lets
its group be corrected.

Double-tap lock uses the accessibility service (no device-admin path). Private
Space is always available on supported devices.

The application ID is `com.bradflaugher.blauncher`. The inherited source and
namespace remain under `app.olauncher`.

## Build

Install JDK 17 and Android SDK Platform **37.0** (`platforms;android-37.0`),
then run:

```sh
./gradlew lint test assembleDebug
```

An unsigned release build can be produced with:

```sh
./gradlew assembleRelease
```

Local builds default to version code `1` and version name `1.0`. Override them
for automated builds with `BLAUNCHER_VERSION_CODE` and
`BLAUNCHER_VERSION_NAME`:

```sh
BLAUNCHER_VERSION_CODE=42 BLAUNCHER_VERSION_NAME=1.0.42 ./gradlew assembleRelease
```

## Signed Releases

Set all four signing variables before building a signed release:

```sh
export BLAUNCHER_KEYSTORE_PATH=/absolute/path/to/blauncher.jks
export BLAUNCHER_STORE_PASSWORD=store-password
export BLAUNCHER_KEY_ALIAS=key-alias
export BLAUNCHER_KEY_PASSWORD=key-password
./gradlew assembleRelease
```

Signing is all-or-nothing: a release is unsigned when none of these variables
are set, and configuration fails when only some are set.

Every push to `main` runs lint, tests, and a signed release build in GitHub
Actions. It replaces the single `latest` release and its `Blauncher.apk`,
publishing a `Blauncher.apk.sha256` checksum alongside it (see `SECURITY.md`).
Configure these repository secrets:

- `BLAUNCHER_KEYSTORE_BASE64`: the keystore encoded with base64.
- `BLAUNCHER_STORE_PASSWORD`
- `BLAUNCHER_KEY_ALIAS`
- `BLAUNCHER_KEY_PASSWORD`

Pull requests run the same checks and produce an unsigned release APK without
publishing a GitHub release.

## License And Attribution

Blauncher is licensed under the GNU General Public License v3.0; see `LICENSE`.
It is derived from Olauncher by Tanuj Notes:
https://github.com/tanujnotes/Olauncher

Modifications in this repository are part of the Blauncher hard fork and are
released under the same GPL-3.0 terms.
