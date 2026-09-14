# Blauncher

A near-empty Android launcher: black screen, the date, and along the bottom a
search bar and a password-manager shortcut. No app icons, dock, or widgets.

Personal hard fork of [Olauncher](https://github.com/tanujnotes/Olauncher) — not an official release.

There are no on-screen hints. **Swipe up** for apps, **long-press home** for
settings. Full details in the **[user guide](GUIDE.md)**.

## Screens

<p align="center">
  <img width="32%" src="docs/screenshots/home.png" alt="Home screen: the date at the top, and at the bottom a search bar beside a round key glyph for the password manager">
  <img width="32%" src="docs/screenshots/drawer.png" alt="App drawer: apps listed in colored groups with a search field on top; emphasized apps are bold and first while the rest of their group folds into one faded line">
  <img width="32%" src="docs/screenshots/settings.png" alt="Settings: Blauncher, Smart ordering, and Home screen cards">
</p>

<p align="center"><sub>Home &nbsp;·&nbsp; App drawer &nbsp;·&nbsp; Settings</sub></p>

## What it does

- **Near-empty home.** The date at the top, and along the bottom a search bar
  beside a key glyph that opens your password manager, with the notification
  bar always on. The search bar is a small composer; sending opens the
  results page for your chosen engine (divid3 by default, or DuckDuckGo,
  Google, Bing, Brave, Kagi, Startpage, Ecosia, Perplexity, ChatGPT) in the default
  browser in one tap, or hands the text to the browser's own engine if you
  prefer. The password shortcut binds itself to an installed password
  manager (Bitwarden, 1Password, Proton Pass, KeePassDX, and others) and can
  be pointed at any app. Gestures do the rest: swipe up for the drawer, swipe
  down for notifications or search, swipe left/right for two chosen apps,
  long-press for settings.
- **Keyboard-first drawer.** The keyboard opens with the drawer. Type to
  filter; a single match launches itself; Enter launches the first match. No
  match? Enter sends the query to your search app. Prefix `!` for DuckDuckGo,
  or a space to browse without auto-launch.
- **Grouped apps.** Apps are categorized on-device into groups such as AI
  Agents, People, Focus, News, Media, and Tools, each with a small colored
  glyph. Heuristic and local. Long-press an app → **Group** to see which
  groups it is under and pick others, including several at once (search still
  dedupes). The same sheet has an **Emphasize** switch: emphasized apps go
  bold and rise to the top of their group while the rest of that group folds
  into one faded line (`+5 · Gemini · Perplexity · …`) that expands on tap.
  Search still matches folded apps, so nothing is ever hidden for good.
  Long-press an app's colored glyph to toggle emphasis in place. The
  home-screen date has its own **Bold date** switch.
- **Smart group order.** Groups follow time of day — news in the morning,
  focus during work, media in the evening — then sharpen from the apps you
  actually open (hour and weekday/weekend buckets, two-week half-life). Apps
  stay alphabetical inside each group, with emphasized apps first. Pin any
  groups to the top; **AI Agents is pinned first by default**. Reset learning
  in Settings.
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
