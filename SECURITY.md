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
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, Dependabot keeps dependencies and action pins current, and CodeQL
  scans both the Kotlin sources and the workflows.
