# Blauncher

A text-only Android launcher that gets out of the way. The home screen is a
black screen with the date, a search bar, and a shortcut to your password
manager. No icons, dock, or widgets. Everything else is a gesture away.

**Swipe up** for your apps. **Long-press** for settings. That is the whole
interface; the **[user guide](GUIDE.md)** covers the rest.

## Screens

<p align="center">
  <img width="32%" src="docs/screenshots/home.png" alt="Home screen: the date at the top, and at the bottom a search bar beside a round key glyph for the password manager">
  <img width="32%" src="docs/screenshots/drawer.png" alt="App drawer: apps listed under an empty search field, each group marked by its own colored icon; emphasized apps are bold and first while the rest of the group folds into one faded line counting the hidden apps">
  <img width="32%" src="docs/screenshots/settings.png" alt="Settings: Blauncher, Smart ordering, and Home screen cards">
</p>

<p align="center"><sub>Home &nbsp;·&nbsp; App drawer &nbsp;·&nbsp; Settings</sub></p>

## What it does

- **A home screen with almost nothing on it.** The date up top; along the
  bottom, a search bar and a key glyph that opens your password manager. The
  search bar sends your query to the engine you choose (divid3 by default, or
  DuckDuckGo, Google, Bing, Brave, Kagi, Startpage, Ecosia, Perplexity,
  ChatGPT) in your default browser, or hands it to the browser's own engine.
  The key glyph finds an installed password manager (Bitwarden, 1Password,
  Proton Pass, KeePassDX, and others) on its own and can be pointed at any
  app. Swipe down for notifications, swipe left or right for two apps of your
  choice.
- **Type, don't hunt.** The keyboard opens with the drawer. Type a few
  letters: a single match launches itself, and Enter launches the first one.
  No match? Enter sends the text to the web. Prefix `!` for DuckDuckGo, or a
  leading space to browse without auto-launch.
- **Apps in groups, your picks on top.** Apps are sorted on-device into groups
  such as AI Agents, People, Focus, News, Media, and Tools, each with a small
  colored glyph. Long-press an app to change its groups, including several at
  once, or to **emphasize** it: emphasized apps go bold and rise to the top of
  their group while the rest fold into one faded line
  (`+5 · Gemini · Perplexity · …`) that expands on tap. Search still matches
  folded apps, so nothing is ever out of reach. Long-press a glyph to toggle
  emphasis in place.
- **An order that follows your day.** Groups shift with the time of day, news
  in the morning, focus during work, media in the evening, then sharpen from
  the apps you actually open. Apps stay alphabetical inside each group with
  emphasized ones first. Pin any groups to the top; AI Agents is pinned by
  default. Reset the learning any time in Settings.
- **Private by construction.** No internet permission, no usage-stats access,
  no accounts, sync, analytics, accessibility service, or launcher-managed
  wallpaper. What it learns lives in local preferences excluded from backups.
  Private Space is supported with a tap-to-unlock row at the bottom of the
  drawer. Details in [`SECURITY.md`](SECURITY.md).

## Latest Android only

Blauncher targets **only the latest public stable Android**: `minSdk`,
`targetSdk`, and `compileSdk` are the same, current API level, and the
toolchain tracks the latest Android Gradle Plugin and Gradle. Older Android
is not supported. Full policy in [`AGENTS.md`](AGENTS.md).

## Install

1. Download `Blauncher.apk` from the
   [latest release](https://github.com/bradflaugher/Blauncher/releases/tag/latest).
   Optionally verify it against `Blauncher.apk.sha256`
   (see [`SECURITY.md`](SECURITY.md)).
2. Install it, open it, and tap **Set as default launcher**.

Requires the latest stable Android.

## Build

Toolchain versions are pinned in Gradle.

```sh
./gradlew lint test assembleDebug   # what CI runs on every PR
./gradlew assembleRelease           # unsigned release
```

CI passes the version in through the environment:

```sh
BLAUNCHER_VERSION_CODE=42 BLAUNCHER_VERSION_NAME=1.0.42 ./gradlew assembleRelease
```

The application ID is `com.bradflaugher.blauncher`; the source namespace is
`app.olauncher`.

### Signed releases

Set all four variables or none. A partial configuration fails the build.

```sh
export BLAUNCHER_KEYSTORE_PATH=/absolute/path/to/blauncher.jks
export BLAUNCHER_STORE_PASSWORD=store-password
export BLAUNCHER_KEY_ALIAS=key-alias
export BLAUNCHER_KEY_PASSWORD=key-password
./gradlew assembleRelease
```

### CI

Every push to `main` runs lint and tests, builds a signed release, and
replaces the `latest` release with `Blauncher.apk` and `Blauncher.apk.sha256`.
Pull requests run the same checks and build an unsigned APK without
publishing. CodeQL scans Kotlin and the workflows on every push and weekly.

Secrets: `BLAUNCHER_KEYSTORE_BASE64`, `BLAUNCHER_STORE_PASSWORD`,
`BLAUNCHER_KEY_ALIAS`, `BLAUNCHER_KEY_PASSWORD`.

## Docs

- [`GUIDE.md`](GUIDE.md): gestures, screens, settings
- [`SECURITY.md`](SECURITY.md): reporting, release verification, privacy
- [`AGENTS.md`](AGENTS.md): contributor and agent instructions, latest-only policy

## License

GPL-3.0. See [`LICENSE`](LICENSE). Blauncher began as a fork of
[Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj Notes and is
released under the same terms.
