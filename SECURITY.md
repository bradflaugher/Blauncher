# Security

Blauncher is a personal hard fork of Olauncher, distributed as a signed APK
from this repository's GitHub Releases.

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

## Design notes

- The app requests no `INTERNET` permission; nothing is sent off the device.
- Auto backup and data-extraction rules are intentionally empty, so app data
  (including locally learned launch weights) is never included in device
  backups.
- Smart ordering learns only from launches made inside the launcher and
  stores its data in local app preferences; the system usage-stats API is
  never used.
- The home-screen search bar keeps unsent text as a draft in the same local
  preferences (excluded from backups) until it is sent or cleared. Sending
  builds the results URL for the search engine chosen in Settings and opens
  it in the default browser with an `ACTION_VIEW` intent, or, for the
  "Browser default" option, hands the raw text over as an
  `ACTION_WEB_SEARCH` intent. Either way the launcher itself makes no
  network request; from the hand-off on, the text is the browser's and the
  chosen engine's data, subject to their privacy terms.
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, Dependabot keeps dependencies and action pins current, and CodeQL
  scans both the Kotlin sources and the workflows.
