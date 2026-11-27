# Security Policy

## Supported versions

ClashBrowser is a small, actively developed project. Security fixes are made on
the `main` branch. Only the latest released version is supported.

| Version | Supported |
|---|---|
| 1.6.x (latest) | Yes |
| Older versions | No |

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability** (GitHub Security Advisories).
3. Describe the issue, affected version/commit, reproduction steps and impact.

If private reporting is unavailable, contact the maintainer through the contact
information in the repository profile and ask for a private channel. Please do
not include exploit details in a public issue.

You can expect an acknowledgement within a few days. This is a volunteer
project, so no fixed response time or bug bounty can be promised. Please allow
a reasonable disclosure window before publishing details.

## Scope

In scope:

- ClashBrowser application code (`app/src/main/java/com/jomo/clashbrowser`)
- The local proxy integration (WebView `ProxyController`, `ProxyManager`)
- Configuration/subscription handling and file handling
- Android manifest, exported components, `FileProvider` configuration
- Build scripts in this repository (`scripts/`, `app/build.gradle.kts`)

Out of scope (report upstream instead):

- The mihomo proxy core (https://github.com/MetaCubeX/mihomo)
- The GeoIP / GeoSite data (https://github.com/MetaCubeX/meta-rules-dat)
- Android System WebView / Chrome itself
- Third-party subscriptions or nodes used with the app
- Vulnerabilities that require a rooted device or a malicious app with
  equivalent privileges

## Known design facts (not vulnerabilities by themselves)

These are documented behaviours to avoid duplicate reports:

- **Local ports are reachable by other apps.** While the core is running, the
  mihomo mixed port (`127.0.0.1:17890`) and REST API (`127.0.0.1:19090`, empty
  secret) listen on loopback. Other apps on the same device can connect to
  them. The app does not restrict this.
- **`usesCleartextTraffic` is enabled.** The app is a browser and communicates
  with local HTTP ports; it also loads plain HTTP pages by design.
- **`extractNativeLibs` is enabled.** The proxy core must be an executable
  file, so native libraries are extracted at install time.
- **Incognito mode only stops history recording.** Cookies, cache, form data
  and the tab snapshot are not cleared or isolated.
- **The build downloads third-party binaries.** `fetchCoreAssets` downloads
  the core and Geo data from GitHub (falling back to a third-party mirror) and
  does not verify checksums. Treat builds from forks with care and verify the
  downloaded files if this matters to you.
- **No analytics, no crash reporting, no telemetry.** The app contains no
  network endpoint other than what the user configures (subscription, sites).

## Supply chain

Build-time downloads are not pinned to a specific version or checksum, and a
third-party mirror (`ghfast.top`) is used as a fallback. This is a known
weakness. If you build from source, you may pre-place verified copies of:

- `app/src/main/jniLibs/arm64-v8a/libmihomo.so`
- `app/src/main/assets/Country.mmdb`
- `app/src/main/assets/geosite.dat`

so that the build does not download anything. Pull requests that add version
pinning and checksum verification are welcome.
