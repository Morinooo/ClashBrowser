# ClashBrowser

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Android build](https://github.com/Morinooo/ClashBrowser/actions/workflows/android.yml/badge.svg)](https://github.com/Morinooo/ClashBrowser/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(arm64--v8a)-green.svg)](#requirements)

**A lightweight Android browser with an embedded mihomo core — it proxies only its own WebView, without a VPN service.**

ClashBrowser is a small, ad-free Android browser with a built-in [mihomo](https://github.com/MetaCubeX/mihomo) (Clash Meta) core and an in-app proxy panel. Traffic from the browser is routed through the local mihomo mixed port via AndroidX WebKit's `ProxyController`, so the app can reach proxied sites and DRM streaming services **without requesting the Android VPN permission** and without affecting any other app on the device.

> This is an independent project. It is **not** affiliated with, endorsed by, or maintained by MetaCubeX, the Clash project, or the mihomo project.

[中文文档](README.zh-CN.md)

---

## Download

Signed APKs are published in **[Releases](https://github.com/Morinooo/ClashBrowser/releases)**.

- [Latest release](https://github.com/Morinooo/ClashBrowser/releases/latest)
- Requires **Android 8.0+ (API 26)** on an **arm64-v8a** device
- Verify the download with the `SHA256SUMS` asset: `sha256sum -c SHA256SUMS`
- Release APKs are built and signed by the release workflow when a `v*` tag is pushed; they are not debug builds
- Not distributed via Google Play

ClashBrowser does **not** bundle any subscription, node or proxy service. You need your own Clash-compatible subscription or configuration.

---

## Why another browser?

Most ways to browse through a proxy on Android force a trade-off:

| | Regular browser | System-wide proxy/VPN client | ClashBrowser |
|---|---|---|---|
| Needs VPN permission | No | Yes | **No** |
| Affects other apps | No | Yes | **No** |
| Built-in node/profile management | No | Usually | **Yes** |
| Proxy survives core being killed | — | Client-dependent | **Auto-revive in foreground, restart on return** |
| Extra apps required | — | Browser + client | **One app** |

The idea is simple: put the Clash configuration centre *inside* the browser, and scope the proxy to the browser's own network stack.

## Features

### Browsing
- Multi-tab browsing, one lazily-created WebView per tab; tab switcher, long-press tab counter to open a new tab
- Compact address bar: direct URL, `https://` auto-prefix, or Google search; focus expands the bar and shows a clear button
- Forward / back / reload / stop, load progress, home button
- `target="_blank"` / `window.open()` opened as a new tab (toggleable)
- Fullscreen HTML5 video with back-button exit

### Embedded proxy core
- Bundled mihomo core runs as a **child process**; started, stopped and restarted by the app
- WebView traffic proxied through `127.0.0.1:7890` with `androidx.webkit.ProxyController` (no VPN, no root)
- Subscription import (URL), YAML validation, atomic config writes, backup self-healing, hot reload
- Profile cards with name, traffic usage / total, expiry date, usage bar and relative update time (from the `subscription-userinfo` header)
- Node and policy-group list (Selector / URLTest / Fallback / LoadBalance), per-node latency test, batch speed test
- Run mode switching: Global / Rule / Direct (`PATCH /configs`); in Global mode the `GLOBAL` selector is corrected if it points at `DIRECT`
- Full-screen YAML editor for `config.yaml`
- Live core log viewer
- Proxy master switch (off = direct connection and core torn down)

### Core resilience
- Wait for `/version` readiness before allowing page loads
- One automatic retry after a failed start, stale core process cleanup via `/proc`
- Auto-revive ~1.5 s after the core is killed while the app is in the foreground; restart and re-bind the proxy when returning to foreground
- Config is restored from backup only when missing — never silently reset
- Core is force-killed when the task is swiped away (nothing left running in the background)

### Browsing data
- Local home page: logo, search box, up to 8 circular shortcut icons chosen from bookmarks
- Bookmarks: nested folders, rename / edit URL / move / delete / multi-select, search, delete-all, Netscape HTML import and export (folder hierarchy preserved, duplicates skipped)
- History (max 500 entries) with view / open / delete / clear; incognito toggle
- Session restore: if the process is killed, the next cold start offers to reopen the previous tabs

### Comfort and media
- Appearance: Light / Dark / Follow system; WebView is rebuilt on theme change while preserving per-tab back/forward history
- Algorithmic darkening / Force Dark for web pages where supported
- Desktop or mobile user agent
- Long-press context menu: open in background, open in new tab, view image, save image, download image, share image, image gallery mode, page info, mark ad domain, copy link text, scan QR code, copy link, share
- Save images through MediaStore (API 29+) or app-specific storage (API 26–28); share via `FileProvider`; QR decoding with ZXing
- Download manager for non-image files: task list with live progress, retry, open-with and confirmed delete; system `Download/ClashBrowser` on API 29+, app-private dir below; follows the in-app proxy and session cookies
- File upload: `<input type="file">` opens the system file picker (multi-select) via SAF
- In-page find with live match count and prev/next; reader mode (clean article reflow, adjustable font size); media sniffer (per-tab stream list with copy/download/open)
- Address-bar suggestions / autocomplete: local history/bookmark matches plus the current engine's suggest endpoint, debounced and cancellable, off in incognito, toggle in Toolbox → More
- Geolocation API: per-origin allow/deny dialog with remember choice, system runtime permission, and manageable permission records in Toolbox → More
- Toolbox → More consolidates settings and page tools: search engine choice (Google/Bing/Baidu/DuckDuckGo), incognito isolation (no history/snapshot/cache; cookies of incognito-visited hosts cleared on exit), subscription info (hardened userinfo parsing + refresh), reader mode and media sniffer. Desktop mode (UA + real desktop viewport) lives in the standalone 浏览器标识 main-list entry; the top status banner is dismissible
- DRM/EME hardening: grant `RESOURCE_PROTECTED_MEDIA_ID`, third-party cookies, standard Chrome mobile UA (`wv` marker removed), license-provider and media-CDN allow-list, 4-second EME probe timeout with graceful fallback
- Password-field hardening: rewrite the IME input type to the native password variation, disable personalised learning, and rebuild the input connection on focus so OEM secure keyboards (e.g. vivo) are triggered reliably
- Lightweight host-list ad/tracker blocking with a user-marked ad-domain list

## Screenshots

Screenshots are not committed yet — no fabricated images are used in this repository. See [docs/screenshots/README.md](docs/screenshots/README.md) for the planned shots and how to contribute them.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 1.9.24 (JVM target 17) |
| UI | Jetpack Compose (BOM 2024.06.00), Material 3, single `ComponentActivity` |
| Browser engine | Android System WebView + `androidx.webkit` 1.11.0 |
| Proxy core | mihomo (Clash Meta) prebuilt `android-arm64-v8a` executable, run as a child process |
| Networking | OkHttp 4.12.0 (subscription fetch, local Clash REST API, media fetch) |
| Config parsing | SnakeYAML 2.2 (validation only) |
| QR | ZXing core 3.5.3 |
| Concurrency | kotlinx.coroutines 1.8.1 (`StateFlow`) |
| Storage | SharedPreferences + app-private files (no database) |
| Build | AGP 8.5.2, Gradle 8.7, JDK 17, `minSdk 26`, `targetSdk 34`, `arm64-v8a` only |

## Architecture

```
                    ┌──────────────────────────────────────────────┐
                    │              MainActivity (Compose)          │
                    │  tabs · address bar · home · bottom toolbar  │
                    └───────┬───────────────┬──────────────┬───────┘
                            │               │              │
                ┌───────────▼───┐   ┌───────▼──────┐  ┌────▼──────────┐
                │  ProxyPanel   │   │ ToolboxSheet │  │  HomeScreen   │
                │  (BottomSheet)│   │  (BottomSheet)│  │               │
                └───────┬───────┘   └───────┬──────┘  └────┬──────────┘
                        │                   │              │
                        ▼                   ▼              ▼
                ┌─────────────────────────────────────────────────────┐
                │                     ProxyManager                    │
                │  process control · Clash REST API · subscriptions   │
                │  config write/reload · logs · run mode · profiles   │
                └──────┬───────────────────────┬──────────────────────┘
                       │ ProcessBuilder        │ OkHttp
                       ▼                       ▼
        libmihomo.so (child process)   http://127.0.0.1:9090
        mixed-port 127.0.0.1:7890      (/version /proxies /configs /delay)
                       ▲
                       │ HTTP proxy
        ┌──────────────┴──────────────┐
        │  System WebView network     │
        │  ProxyController            │
        │  127.0.0.1:7890             │
        └──────────────┬──────────────┘
                       ▼
             Web pages / DRM streams
```

### How it works

1. On first launch the core binary and Geo databases are placed under the app's `filesDir/mihomo`, the core is started as a child process (`libmihomo.so -d filesDir/mihomo`) and the app waits until `GET /version` responds.
2. The WebView proxy override is applied with `ProxyController.setProxyOverride(http://127.0.0.1:7890)`. Only this app's WebView uses it; the system and other apps are untouched, and no VPN permission is involved.
3. HTTPS requests reach mihomo as `CONNECT` tunnels, so mihomo resolves the destination and applies the subscription's rules and policy groups itself.
4. Configuration is validated, forced to keep `mixed-port: 7890` / `external-controller: 127.0.0.1:9090`, written atomically and hot-reloaded through `PUT /configs?force=true`.
5. The app watches the core process: if it dies while the app is in the foreground it is restarted after ~1.5 s, and if it died while the app was in the background it is restarted on return to foreground.

### Project structure

```
app/src/main/java/com/jomo/clashbrowser/
├── MainActivity.kt        # activity, tabs, WebView factory, address bar, context-menu dispatch
├── ProxyManager.kt        # core process, Clash REST API, subscriptions, profiles, config, logs
├── ProxyPanel.kt          # proxy centre bottom sheet
├── ToolboxSheet.kt        # toolbox: bookmarks, history, appearance, UA, about
├── HomeScreen.kt          # local home page and shortcut editors
├── ContextMenu.kt         # long-press menu, image gallery, page info, QR result
├── MediaActions.kt        # image fetch/save/download/share, QR decode
├── DownloadStore.kt       # download manager: tasks, progress, retry, open/delete
├── BookmarkStore.kt       # bookmark tree + Netscape HTML import/export
├── HomeStore.kt           # home shortcut preferences
├── HistoryStore.kt        # history + incognito flag
├── SessionStore.kt        # tab snapshot, clean-exit flag, restore prompt
├── AdBlocker.kt           # host-list ad/tracker blocking
├── SecureWebView.kt       # password keyboard hardening
├── WebViewProxyHelper.kt  # WebView ProxyController binding
├── CoreKeeperService.kt   # task-removal sentinel (kills the core, marks clean exit)
└── UiTheme.kt             # theme helpers, WebView dark-mode sync
```

## Requirements

- Android 8.0 (API 26) or newer, **arm64-v8a** device
- A reasonably up-to-date Android System WebView
- A Clash-compatible subscription (or a hand-written `config.yaml`)
- Build environment: JDK 17, Android SDK Platform 34, network access on the first build

## Build from source

```bash
git clone <this-repository>
cd ClashBrowser
./gradlew assembleDebug
```

On first build, the `fetchCoreAssets` Gradle task downloads the following into the source tree if missing:

- `app/src/main/jniLibs/arm64-v8a/libmihomo.so` — official `mihomo-android-arm64-v8a` build from GitHub releases
- `app/src/main/assets/Country.mmdb` — `country-lite.mmdb` from MetaCubeX/meta-rules-dat
- `app/src/main/assets/geosite.dat` — from MetaCubeX/meta-rules-dat

These binaries are **not committed to this repository** (see `.gitignore`) for size and licensing reasons. Network access to GitHub is required; the build script falls back to a third-party mirror if GitHub is unreachable. See [SECURITY.md](SECURITY.md) for the supply-chain caveat.

Offline alternative (Windows):

```powershell
pwsh -File scripts/fetch-assets.ps1
```

or place the three files at the paths above manually.

Output: `app/build/outputs/apk/debug/app-debug.apk`

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Release signing is not configured in this repository. Debug APKs are for testing only; do not distribute them.

## Usage

1. Launch the app. The core starts automatically; the status dot is green when the proxy is effective and red when browsing directly.
2. Tap the status dot to open the proxy centre, paste a subscription URL and update it. The YAML is validated and hot-reloaded.
3. Pick a node in a Selector group; use "Test all" for latencies.
4. Switch between Global / Rule / Direct as needed.
5. "Edit config" opens the raw `config.yaml`; "Core log" shows the live log.

> **Note:** subscription downloads currently go directly (not through the proxy), so the subscription URL itself must be reachable without a proxy.

## Current status

Version **1.16.0** (`versionCode 24`), actively developed. Version names follow
semver (feature = minor, fix = patch); see CHANGELOG for the old→new map.

| Area | Status |
|---|---|
| Browsing, tabs, home page, bookmarks, history, session restore | Implemented |
| Embedded mihomo core, profile/subscription management, nodes, modes, logs | Implemented |
| WebView local proxy without VPN | Implemented |
| Core lifecycle hardening (retry, revive, clean shutdown) | Implemented |
| Bookmarks import/export, image actions, QR, ad blocking | Implemented |
| Download manager (progress / retry / open / delete), file upload (SAF) | Implemented |
| In-page find, reader mode, media sniffer | Implemented |
| Search-engine choice, desktop viewport, incognito isolation, subscription info | Implemented |
| JVM unit tests + CI lint gate | Implemented |
| Release signing via CI secrets (tag builds) | Implemented |
| Site permission management | **Not implemented** |

## Known limitations

- **arm64-v8a only.** 32-bit devices and x86 emulators are not supported.
- **Only the app's own WebView is proxied.** This is by design; other apps need their own proxy.
- **Subscriptions are fetched directly**, so a blocked subscription endpoint cannot be updated from inside the app.
- **No VPN service.** Apps or system components that do not go through WebView are not proxied.
- **Incognito isolates history, session snapshot, cache and form data, and clears cookies of hosts visited while incognito on exit.** Cookies of normal tabs are untouched; it is still not a separate process/profile sandbox.
- **Site permission management is not implemented.**
- **Ad blocking is a small built-in host list** plus user-marked domains; it is not a rule-subscription engine and cannot be toggled.
- **DRM playback depends on the system WebView provider and Widevine availability.** On devices without a usable CDM, the app falls back to non-encrypted streams after a 4-second EME probe timeout.
- **The proxy applies only if the WebView provider supports `ProxyController`**; otherwise the app silently falls back to a direct connection.
- **The local mihomo API (`127.0.0.1:9090`, empty secret) and mixed port (`127.0.0.1:7890`) are reachable by other apps on the same device while the core is running.**
- **The core and Geo data are downloaded at build time from GitHub's "latest" release without checksum pinning**, which makes builds non-reproducible.
- **Two signing channels:** CI tag builds produce a release-signed APK; manually delivered APKs are debug-signed. The two signatures cannot overwrite-install each other — pick one channel.
- **Run-mode preference** is re-applied when a configuration is saved or the core is restarted manually; after a cold start the mode stored in `config.yaml` is used.

## Roadmap

These are intended directions, not promises:

- Pin core/Geo data versions and verify checksums during the build
- Release signing setup and a reproducible release workflow
- Screenshots and short demo recordings
- Additional ABI support (if there is demand)

## Privacy

- Only two permissions are declared: `INTERNET` and `ACCESS_NETWORK_STATE`.
- No analytics, no crash reporting, no telemetry, no ads.
- Subscriptions, history, bookmarks and settings stay in app-private storage (`SharedPreferences` and `filesDir`). `allowBackup` is disabled.
- The app contains no bundled subscription link, node, API key or server address.
- While the proxy is running, its loopback ports are reachable by other apps on the same device (see Known limitations).

## Disclaimer

ClashBrowser is a browser with an embedded proxy core. It does not provide, sell or bundle any proxy service, subscription or node. You are responsible for the subscriptions and nodes you use, for complying with the laws and regulations of your jurisdiction, and for respecting the terms of service of the websites you visit. The software is provided "as is", without warranty of any kind. Using the app to circumvent network restrictions may be illegal in some countries; use it at your own risk.

## License

ClashBrowser is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License, version 3** as published by the Free Software Foundation. See [LICENSE](LICENSE).

The bundled Geo data comes from [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) (GPL-3.0), and the embedded core is [mihomo](https://github.com/MetaCubeX/mihomo) (MIT). See [NOTICE](NOTICE) for third-party attributions, including MaxMind GeoLite2 data attribution.

## Acknowledgements

- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) — the proxy core
- [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) — GeoIP / GeoSite data
- [MaxMind GeoLite2](https://www.maxmind.com) — geolocation data
- Jetpack Compose, AndroidX, AndroidX WebKit — UI and WebView proxy API
- OkHttp, kotlinx.coroutines, SnakeYAML, ZXing — networking, concurrency, parsing, QR decoding

## Contributing

Bug reports and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first. For security issues, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
