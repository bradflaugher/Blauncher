# Blauncher

A text-only Android launcher: black screen, a clock, and your apps by name.
No icons, dock, widgets, or on-screen buttons.

Personal hard fork of [Olauncher](https://github.com/tanujnotes/Olauncher) — not an official release.

There are no on-screen hints. **Swipe up** for apps, **long-press home** for
settings. Full details in the **[user guide](GUIDE.md)**.

## Screens

| Home | App drawer | Settings |
| :---: | :---: | :---: |
| ![Home screen: clock, date, and a short text list of apps](docs/screenshots/home.png) | ![App drawer: apps listed in colored groups with a search field on top](docs/screenshots/drawer.png) | ![Settings: Blauncher, Smart ordering, and Home screen cards](docs/screenshots/settings.png) |

## What it does

- **Near-empty home.** Clock, date with battery, and up to eight apps (four by
  default) as plain text. Gestures do the rest: swipe up for the drawer, swipe
  down for notifications or search, swipe left/right for two chosen apps,
  long-press for settings.
- **Keyboard-first drawer.** The keyboard opens with the drawer. Type to
  filter; a single match launches itself; Enter launches the first match. No
  match? Enter sends the query to your search app. Prefix `!` for DuckDuckGo,
  or a space to browse without auto-launch.
- **Grouped apps.** Apps are categorized on-device into groups such as AI
  Agents, People, Focus, News, Media, and Tools, each with a small colored
  glyph. Heuristic and local. Long-press an app to recategorize it, including
  into several groups at once (search still dedupes).
- **Smart group order.** Groups follow time of day — news in the morning,
  focus during work, media in the evening — then sharpen from the apps you
  actually open (hour and weekday/weekend buckets, two-week half-life). Apps
  stay alphabetical inside each group. Pin any groups to the top; **AI Agents
  is pinned first by default**. Reset learning in Settings.
- **Private by construction.** No internet permission, no usage-stats API, no
  account, sync, analytics, or launcher wallpaper. Learned weights live in
  local preferences excluded from backups. See [`SECURITY.md`](SECURITY.md).
  Private Space is supported (tap-to-unlock row at the bottom of the drawer).
  No accessibility service.

## Latest-only platform

Blauncher tracks **only the latest public stable Android** for `minSdk`,
`targetSdk`, and `compileSdk`, plus the current AGP and Gradle (pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties)).
Older Android is not supported. Full policy: [`AGENTS.md`](AGENTS.md).

## Install

1. Download `Blauncher.apk` from the
   [latest release](https://github.com/bradflaugher/Blauncher/releases/tag/latest).
   Optionally verify the `Blauncher.apk.sha256` checksum
   ([`SECURITY.md`](SECURITY.md)).
2. Install it, open it, and tap **Set as default launcher**.
3. Requires the latest stable Android.

## Build

Use the JDK and SDK pinned in Gradle:

```sh
./gradlew lint test assembleDebug
```

Unsigned release:

```sh
./gradlew assembleRelease
```

CI sets `BLAUNCHER_VERSION_CODE` and `BLAUNCHER_VERSION_NAME`:

```sh
BLAUNCHER_VERSION_CODE=42 BLAUNCHER_VERSION_NAME=1.0.42 ./gradlew assembleRelease
```

Application ID: `com.bradflaugher.blauncher`. Inherited source and namespace
remain `app.olauncher`.

### Signed releases

All four variables, or none (partial config fails the build):

```sh
export BLAUNCHER_KEYSTORE_PATH=/absolute/path/to/blauncher.jks
export BLAUNCHER_STORE_PASSWORD=store-password
export BLAUNCHER_KEY_ALIAS=key-alias
export BLAUNCHER_KEY_PASSWORD=key-password
./gradlew assembleRelease
```

### CI

Pushes to `main` run lint, tests, and a signed release, then replace the
`latest` release (`Blauncher.apk` + `Blauncher.apk.sha256`). PRs run the same
checks and produce an unsigned APK without publishing. CodeQL scans Kotlin and
workflows on every push and weekly.

Secrets:

- `BLAUNCHER_KEYSTORE_BASE64`
- `BLAUNCHER_STORE_PASSWORD`
- `BLAUNCHER_KEY_ALIAS`
- `BLAUNCHER_KEY_PASSWORD`

## Docs

- [`GUIDE.md`](GUIDE.md) — gestures, screens, settings
- [`SECURITY.md`](SECURITY.md) — reporting, release verification, privacy
- [`AGENTS.md`](AGENTS.md) — contributor/agent instructions, latest-only policy

## License

GPL-3.0. See [`LICENSE`](LICENSE). Derived from
[Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj Notes. Fork
changes are released under the same terms.
