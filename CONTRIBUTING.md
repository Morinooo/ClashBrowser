# Contributing to ClashBrowser

Thanks for your interest. This is a small project; please keep changes focused
and easy to review.

## Before you start

- Read [README.md](README.md) and, if you are touching proxy/core code,
  [SECURITY.md](SECURITY.md).
- Check existing issues and pull requests to avoid duplicate work.
- For security problems, follow [SECURITY.md](SECURITY.md) instead of opening
  a public issue.

## Development setup

Requirements:

- JDK 17
- Android SDK Platform 34 (compileSdk/targetSdk 34; minSdk 26)
- Android Studio Koala or newer, or the command line
- Network access on the first build (the core and Geo databases are downloaded
  by `fetchCoreAssets`)

Build and install a debug APK:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, `pwsh -File scripts/fetch-assets.ps1` can pre-fetch the binary
assets.

## Project conventions

- Kotlin only; Jetpack Compose for UI. There is no XML layout in this project.
- Keep the architecture: a single `ComponentActivity`, singleton stores in
  app-private storage, no dependency injection framework, no database.
- No new permissions without a clear need and a discussion in an issue.
- Do not add analytics, crash reporting, ads or telemetry.
- Do not commit:
  - `libmihomo.so`, `Country.mmdb`, `geosite.dat` (downloaded at build time)
  - APKs/AABs
  - keystores, signing configs, `local.properties`, tokens or subscription
    links
- User-facing strings are currently hardcoded in Kotlin (Chinese). If you add
  UI text, match the existing language and tone unless the change is explicitly
  about internationalisation.
- Keep the existing visual language: light/compact cards, Material 3 defaults,
  no emoji in node cards.

## Making a change

1. Fork the repository and create a branch.
2. Keep the change focused. Do not reformat unrelated code.
3. Build locally (`./gradlew assembleDebug`) and, if possible, test on an
   arm64 device.
4. If your change affects behaviour, update the English and Chinese README
   sections that describe it, and add a `CHANGELOG.md` entry.
5. Open a pull request using the template. Describe the problem, the fix and
   how you verified it.

## Commit messages

Conventional, imperative style is preferred, e.g.:

```
fix: keep WebView proxy binding after core restart
feat: add search-engine selection
docs: clarify subscription fetch limitation
```

## Tests

There is currently no automated test suite. New pure-logic code is welcome to
come with JVM unit tests under `app/src/test`. Instrumented tests are not set
up. CI runs `assembleDebug` and `testDebugUnitTest` (which currently contains no
tests) and uploads the APK artifact.

## Release builds

Release APKs are produced by the `Release` workflow
(`.github/workflows/release.yml`) when a `v*` tag is pushed. The workflow reads
the signing keystore and passwords from encrypted GitHub Actions secrets
(`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`) and attaches the signed APK plus `SHA256SUMS` to the
GitHub release.

Contributors never need signing material. Do not commit keystores, passwords or
`local.properties`; debug builds work without any of them.

## Code review

- Changes to proxy/core lifecycle, WebView configuration, permission or file
  handling get extra scrutiny.
- Large refactors are unlikely to be accepted unless discussed first.
- Be patient: this is a volunteer project.
