# Blauncher

A minimal, gesture-driven Android launcher that gets out of the way. The
home screen is a black screen with the date, a search bar, and a shortcut
button (your password manager to start, any app you like after). No app
icons, dock, or widgets. Everything else is a
gesture away.

**Tap the search bar** for your apps. **Long-press** for settings. That is
the whole interface. On first run a small tip card teaches those two gestures, then
what the date and the shortcut do, one at a time, and gets out of the way;
**Settings → Help and FAQ** has the rest on one page, and the
**[user guide](GUIDE.md)** covers everything. Settings also has **Send
feedback** (a new GitHub issue), **Share Blauncher** and **Rate Blauncher**
(its Google Play page; the app never asks for a rating).

## Screens

<p align="center">
  <img width="24%" src="docs/screenshots/tips.png" alt="First run: the home screen with a tip card above the search bar reading Welcome, tip 1 of 3, Swipe up for your apps">
  <img width="24%" src="docs/screenshots/home.png" alt="Home screen: the date at the top, and at the bottom a search bar beside a round shortcut button wearing a key glyph">
  <img width="24%" src="docs/screenshots/drawer.png" alt="App drawer: a one-line tip under the search field, then apps in groups, each marked by its own colored glyph; emphasized apps are bold and first while the rest of the group folds into one faded line counting the hidden apps">
  <img width="24%" src="docs/screenshots/settings.png" alt="Settings: the Blauncher card with Help and FAQ, Send feedback, Share Blauncher and Rate Blauncher, then Smart ordering">
</p>

<p align="center"><sub>First run &nbsp;·&nbsp; Home &nbsp;·&nbsp; App drawer &nbsp;·&nbsp; Settings</sub></p>

## What it does

- **A home screen with almost nothing on it.** The date up top; along the
  bottom, a search bar and a shortcut button. The search bar is one search
  for apps and the web: what you type narrows your apps, listed under the
  bar, to tap, and Enter sends your query to the engine you choose
  (divid3 by default, or DuckDuckGo, Google, Bing, Brave, Kagi, Startpage,
  Ecosia, Perplexity, ChatGPT) in your default browser, or hands it to the
  browser's own engine.
  The shortcut button finds an installed password manager (Bitwarden,
  1Password, Proton Pass, KeePassDX, and others) on its own and wears a key;
  point it at any app and give it any of some thirty line-art glyphs.
  Swipe down for notifications; swipe up, left or right for three apps of
  your choice (the first swipe each way asks which).
- **Home and the drawer are one surface.** Tap the search bar and it rides
  up to the top of the screen with the keyboard, your app categories below
  it: type a few letters and they give way to the matching apps, best first. There is one
  search with one rule: an app opens only when you tap it, and Enter always
  searches the web. Swipe down from the bar or the top of the list, or go
  back, and it all settles back to the quiet home screen.
- **Search, or pick a category.** With nothing typed, your apps come up as a
  short list of categories sorted on-device (AI Agents, People, Focus, News,
  Media, Tools, and more), each with a small colored glyph and its app count.
  Tap one to list its apps; opening another closes it, so the list always
  fits above the keyboard. Long-press an app to change its categories,
  including several at once.
- **An order that follows your day.** Groups shift with the time of day, news
  in the morning, focus during work, media in the evening, sleep and
  meditation from about 8:30 pm, then sharpen from
  the apps you actually open. Apps stay alphabetical inside each group. Pin
  any groups to the top; AI Agents is pinned by
  default. Reset the learning any time in Settings.
- **Private by construction.** No internet permission, no usage-stats access,
  no accounts, sync, analytics, accessibility service, or launcher-managed
  wallpaper. What it learns lives in local preferences excluded from backups.
  It does not ask to see every installed app (`QUERY_ALL_PACKAGES`), only
  the launchable ones it lists. Private Space is supported with a
  tap-to-unlock row at the bottom of the drawer. Details in
  [`SECURITY.md`](SECURITY.md).
- **Phones, tablets, foldables and Chromebooks.** Phones stay in portrait;
  larger screens rotate freely and keep your place when they do. With a
  keyboard or mouse, the wheel, the up arrow or Enter opens the apps, a
  letter key starts a search with it, and right-click opens Settings. Back is predictive everywhere
  but Home, and TalkBack gets every gesture as an action.

## Latest Android, no compatibility code

Blauncher targets the latest public stable Android and the toolchain tracks
the latest Android Gradle Plugin and Gradle. It installs on Android 15 and
up, because that is the newest API the code uses (Private Space), not
because it carries code for older phones: there are no version checks.
Full policy in [`AGENTS.md`](AGENTS.md).

## Install

1. Download `Blauncher.apk` from the
   [latest release](https://github.com/bradflaugher/Blauncher/releases/tag/latest).
   Optionally verify it against `Blauncher.apk.sha256`
   (see [`SECURITY.md`](SECURITY.md)).
2. Install it, open it, and tap **Set as default launcher**.

Requires Android 15 or newer.

## Build

Toolchain versions are pinned in Gradle. Building needs a JDK at least as
new as the Java version `compileOptions` targets in `app/build.gradle` (CI
installs the one named in `.github/workflows/build-release.yml`), and an
Android SDK with the platform and build tools named there.

```sh
./gradlew lint test assembleDebug   # what CI runs on every PR
./gradlew assembleRelease           # unsigned release APK
./gradlew bundleRelease             # unsigned release bundle (.aab) for Google Play
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
replaces the `latest` release with `Blauncher.apk`, `Blauncher.apk.sha256`,
`Blauncher.aab` (for Google Play), and `mapping.txt` (R8 deobfuscation for
Play crash reports). Pull requests run the same checks and build unsigned
artifacts without publishing. The version code is the workflow run number,
so it only goes up; renaming or recreating the workflow would reset it, and
Play rejects any upload whose version code is not higher than the last. CodeQL scans Kotlin and the workflows on every push and weekly.

Secrets: `BLAUNCHER_KEYSTORE_BASE64`, `BLAUNCHER_STORE_PASSWORD`,
`BLAUNCHER_KEY_ALIAS`, `BLAUNCHER_KEY_PASSWORD`.

## Google Play

The store listing (title, descriptions, icon, feature graphic, screenshots,
release notes) lives in `fastlane/metadata/android/en-US` in the layout
`fastlane supply` reads.

1. **Upload.** Take `Blauncher.aab` from the `latest` release and upload it
   to a testing track, with `mapping.txt` as its deobfuscation file (or
   `fastlane supply --aab Blauncher.aab --track internal`). Use Play App
   Signing; to keep Play and sideloaded installs updating each other, enrol
   the existing `BLAUNCHER_*` key as the app signing key and use a separate
   upload key.
2. **App content.**
   - Privacy policy: <https://bradflaugher.com/privacy/blauncher/> (also
     linked from Settings).
   - Data safety: no data collected or shared. The app has no `INTERNET`
     permission, backups and device transfer are disabled, and web searches
     are handed to another app by intent.
   - Ads: none. App access: no login; set Blauncher as the default home app
     to review it.
   - Target audience: not directed at children. Category: Personalization.
3. **Permissions.** `ACCESS_HIDDEN_PROFILES` shows and unlocks Private Space
   in the drawer (only granted to the default home app); expect a
   justification prompt and attach a short video of unlocking it.
   `REQUEST_DELETE_PACKAGES` backs the drawer's Uninstall action, which
   always goes through the system confirmation. `EXPAND_STATUS_BAR` backs
   the swipe-down-for-notifications gesture.
4. **Screenshots** must be 9:16 or 16:9 PNG or JPEG without alpha. Tablet
   screenshots must be real tablet captures. The ones in `images/` are
   captioned captures of a debug build on the Android emulator (Pixel 8,
   Nexus 7 and Pixel Tablet images), made with the scripts in
   `tools/screenshots/`:
   - `prepare-device.sh <serial> phone|seven|ten [landscape]` sizes the
     screen to 9:16 or 16:9, installs the debug APK, makes it the home app,
     blacks out the wallpaper, sets dark mode and System UI demo mode, and
     brings the first-run tips back (the debug build takes
     `--ez reset_tips true` and `--ez skip_tips true` on its launch intent,
     for screenshot and test runs).
   - `capture.sh <serial> tools/screenshots/raw/<device>/<name>.png` saves
     the current screen as RGB.
   - `caption.py` (`uv run --with pillow tools/screenshots/caption.py`)
     adds the headline and subline from `captions.tsv` in Roboto Light on
     black, and writes every listed shot into `images/`. Pull the font once
     with `adb pull /system/fonts/Roboto-Regular.ttf tools/screenshots/Roboto.ttf`.
   - The README images in `docs/screenshots` are the raw captures, without
     captions.

## Docs

- [`GUIDE.md`](GUIDE.md): gestures, screens, settings
- [`SECURITY.md`](SECURITY.md): reporting, release verification, privacy
- [`AGENTS.md`](AGENTS.md): contributor and agent instructions, latest-only policy

## License

GPL-3.0. See [`LICENSE`](LICENSE). Blauncher began as a fork of
[Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj Notes and is
released under the same terms.
