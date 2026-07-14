# Blauncher

Blauncher is a personal hard fork of Olauncher. It is maintained for a focused,
private Android launcher setup rather than as an official Olauncher release.

## Focus

- Local, private app categorization.
- Time-aware app ranking based on a fixed daily routine and app-name hints.
- A minimal launcher experience without a remote account or synchronization.
- Android 16 only: the minimum, target, and compile SDK are all API 36.

Ranking never reads or stores launch history. It guesses likely activities from
the current hour: early reading, morning commute, work blocks, noon fitness,
family time, and evening books or audiobooks.

The application ID is `com.bradflaugher.blauncher`. The inherited source and
namespace remain under `app.olauncher`.

## Build

Install JDK 17 and Android SDK Platform 36, then run:

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
Actions. It publishes a GitHub release tagged `build-<run_number>`. Configure
these repository secrets:

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
not endorsed by the upstream project.
