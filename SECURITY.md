# Security

Blauncher is a personal hard fork of Olauncher, distributed as a signed APK
from this repository's GitHub Releases. The Google Play build is the
`Blauncher.aab` from the same CI run.

## Reporting a vulnerability

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/bradflaugher/Blauncher/security/advisories/new)
rather than opening a public issue.

## Verifying a release

Each release APK is built by GitHub Actions from the `main` branch and
published alongside a `Blauncher.apk.sha256` checksum. Verify a download with:

```sh
sha256sum -c Blauncher.apk.sha256
```

Each APK and bundle also carries a signed build provenance attestation,
which proves it was built by this repository's CI from a specific commit:

```sh
gh attestation verify Blauncher.apk --repo bradflaugher/Blauncher
```

## Design notes

- The app requests no `INTERNET` permission; nothing is sent off the device.
- The app does not hold `QUERY_ALL_PACKAGES`. It sees only launchable apps
  (what the drawer lists), the home app, and the default browser and
  web-search handler, declared as `<queries>` in the manifest.
- Backups are off: `android:allowBackup="false"`, and the data-extraction
  rules exclude every domain from both cloud backup and device-to-device
  transfer (which ignores `allowBackup`), so app data (including locally
  learned launch weights) is never included in a backup or device migration.
- Smart ordering learns only from launches made inside the launcher and
  stores its data in local app preferences; the system usage-stats API is
  never used.
- The home-screen search bar keeps unsent text as a draft in the same local
  preferences (excluded from backups) until it is sent or cleared. Matching
  the text against app names happens in memory, against the app list shown
  under the bar; opening an app from there sends nothing anywhere. Sending
  the text as a web search
  builds the results URL for the search engine chosen in Settings and opens
  it in the default browser with an `ACTION_VIEW` intent, or, for the
  "Browser default" option, hands the raw text over as an
  `ACTION_WEB_SEARCH` intent. Either way the launcher itself makes no
  network request; from the hand-off on, the text is the browser's and the
  chosen engine's data, subject to their privacy terms.
- **Send feedback**, **Share Blauncher** and **Rate Blauncher** in Settings
  are plain intents: an `ACTION_VIEW` of the GitHub new-issue page in the
  browser, the system share sheet (`ACTION_SEND`) with a fixed line and the
  Play link, and an `ACTION_VIEW` of the Play listing (`market://`, falling
  back to the https page). The launcher adds nothing about the device or its
  apps to any of them, uses no in-app review library, and never prompts for
  a rating.
- The swipe-down notifications gesture calls the hidden
  `StatusBarManager.expandNotificationsPanel()` method by reflection (there
  is no public equivalent) under the `EXPAND_STATUS_BAR` permission. If a
  future Android blocks that method, the gesture silently does nothing.
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, Dependabot keeps dependencies and action pins current, and CodeQL
  scans both the Kotlin sources and the workflows.
