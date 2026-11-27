# Changelog

All notable user-visible changes to ClashBrowser. This file is derived from the
in-app changelog shipped with the app; dates are not recorded there and are
therefore omitted.

**Versioning policy (semver):** `MAJOR.MINOR.PATCH` — MINOR for new features /
performance work / new protocol support (backward compatible), PATCH for bug
fixes, security patches and copy/UI detail tweaks, MAJOR for breaking changes
(none so far). `versionCode` keeps its historical monotonic sequence so
installed apps can always upgrade; only `versionName` / tags were reorganized.

**Renumbering map (old → new):** 1.0→1.0.0, 1.1→1.1.0, 1.2→1.2.0, 1.3→1.3.0,
1.4→1.3.1, 1.4.1→1.4.0, 1.4.2→1.5.0, 1.4.3→1.6.0, 1.4.4→1.6.1, 1.4.5→1.6.2,
1.6.0→1.7.0, 1.6.1→1.8.0, 1.6.2→1.9.0, 1.6.3→1.10.0, 1.6.4→1.11.0,
1.6.5→1.11.1, 1.6.6→1.12.0, 1.6.7→1.13.0, 1.6.8→1.13.1, 1.6.9→1.13.2,
1.7.0→1.14.0. GitHub releases were retagged accordingly (v1.13.0 / v1.13.1 /
v1.13.2 / v1.14.0); older history never had published releases.

## [1.16.2] — versionCode 26

### Fixed
- Deleting a subscription profile in the proxy panel did not remove the previous
  profile's policy groups / nodes from the panel, and a deleted profile kept
  running if it was the active one. Deleting now asks for confirmation, applies
  the next remaining profile automatically, resets to the default direct config
  when the last profile is removed, and clears node/group caches whenever the
  core is not running (no more "内核未运行" with a stale node list).
- The embedded core held the Clash-default local ports while the app sat in the
  background, so another proxy app started afterwards could fail to bind
  `7890` / `9090` (and DNS `1053`) and appear broken. The core now listens on
  dedicated ports — `mixed-port: 17890`, `external-controller
  127.0.0.1:19090`, DNS `127.0.0.1:11053` — with automatic fallback to the next
  free port when a dedicated port is already occupied. Subscription-provided
  `dns.listen` is rewritten to the dedicated port as well.

### Changed
- Docs (EN/ZH README, SECURITY), the in-app About version/changelog, and the
  proxy panel status line now reference the dedicated ports.

## [1.16.1] — versionCode 25

### Security
- Local port & API isolation: config pins `bind-address: 127.0.0.1`; random
  per-start REST secret with Bearer auth on every Clash API call; orphan
  kernel cleanup retained.
- WebRTC leak prevention: document-start disable of RTCPeerConnection, forced
  in incognito; new Toolbox → More switch for normal tabs (default off).
- Subscription/log redaction (query & fragment masked), Android Keystore
  AES/GCM at-rest encryption for subscription list/pointer/profiles/config
  master with runtime-plaintext wipe on process exit; bookmark import capped
  at 5 MB / 20k tokens, subscription & YAML at 2 MB with parse fallbacks.
- WebView boundaries: document-start scripts scoped to http/https origins;
  blocked external schemes now toast; FileProvider grants re-audited minimal.
- Supply chain: mihomo pinned v1.19.34 + SHA-256 verified at build; Geo
  databases vendored at pinned hashes; release `debuggable=false` + R8
  minification with JsInterface/SnakeYAML keep rules; signing stays in CI.

## [1.16.0] — versionCode 24

### Added
- Address-bar search suggestions / autocomplete: while typing, a dropdown shows
  local history/bookmark matches plus the current engine's suggest endpoint
  (Google / Bing / Baidu / DuckDuckGo), 250 ms debounce with cancellable
  requests, capped at 8 rows; tapping a row loads the URL or runs the search.
  Disabled in incognito; toggle in Toolbox → More (default on). Suggest traffic
  uses the same direct channel as subscription fetches.
- Geolocation API support: `onGeolocationPermissionsShowPrompt` opens an origin
  confirmation dialog (allow / deny + remember choice); allowing then requests
  the system runtime permission (ACCESS_FINE/COARSE_LOCATION, added to the
  manifest). Decisions persist per origin (GeoStore); Toolbox → More →
  定位权限记录 lists them with per-row delete and clear-all (both confirmed).
  Runtime denial revokes a stored allow; hide-prompt cancels the callback.

### Notes
- In-page find (清单 item 6) already shipped in 1.15.0; no change this round.

## [1.15.1] — versionCode 23

### Fixed
- Long-press inside web-page input fields (and contenteditable nodes) no longer
  opens the custom link/image context menu: `EDIT_TEXT_TYPE` hit results are
  passed through to WebView's native selection / select-all / cut / copy /
  paste action mode.
- Subscription URL field: added an explicit paste button (reads the clipboard)
  so pasting works even on devices where the system long-press floating
  toolbar is suppressed inside bottom sheets; native long-press is unchanged.

### Changed
- The top proxy/core status banner can now be dismissed via a close (✕) button
  on its right; it reappears automatically when the proxy/core state changes.
- Toolbox layout: 阅读模式 and 资源嗅探 moved into Toolbox → More; 浏览器标识
  (mobile / desktop UA + desktop viewport) restored as a standalone main-list
  entry with its own page, replacing the desktop switch inside More.

## [1.15.0] — versionCode 22

### Added
- In-page find: top-bar search entry opens a find bar with live match count
  (`setFindListener`), previous/next navigation and clear-on-close; matches are
  cleared when switching tabs.
- Reader mode: toolbox entry extracts the article body from a cloned DOM
  (noise nodes removed, live page untouched) into a clean full-screen reader
  with adjustable font size (12–26 sp).
- Media sniffer: media/stream requests (m3u8/mpd/mp4/m4s/ts/flv/webm/mkv/mp3/
  m4a/aac/ogg/wav) are recorded per tab (dedup, cap 200) and listed in
  Toolbox → 资源嗅探 with copy / download / open-with actions and confirmed
  clear. URL metadata only — no response bodies, no disk writes.
- Toolbox → More now consolidates all settings-type controls:
  - Search engine choice (Google / Bing / Baidu / DuckDuckGo) replaces the
    hard-coded Google search for address-bar and home-box queries.
  - Desktop mode: desktop UA **plus** a real desktop viewport (viewport meta
    overridden to width=1024 with wide-view-port/overview), applied to all
    live tabs on toggle.
  - Incognito isolation: incognito tabs write no history, no session snapshot,
    no cache and no form data; hosts visited while incognito are tracked and
    their cookies expired plus WebView cache cleared when incognito ends
    (switch off or last incognito tab closed). Normal tabs' cookies untouched.
  - Subscription info page: parsed upload/download/total/expire/updated for
    the active profile with a manual refresh action.

### Changed
- Toolbox main list: 下载管理 stays a standalone entry; 阅读模式 / 资源嗅探
  added; the former 浏览器标识 page and the main-page incognito switch moved
  into More (as desktop-mode / incognito switches).
- `subscription-userinfo` parsing hardened: key case, surrounding spaces,
  quoted values, missing fields, non-numeric values and millisecond
  timestamps tolerated; negatives clamped to 0. Covered by new JVM tests.
- Version numbers reorganized per semver (see map above); in-app changelog,
  README and release notes rewritten to the new scheme.

## [1.14.0] — versionCode 21 (was 1.7.0)

### Added
- Download manager: non-image downloads captured via `setDownloadListener`,
  streamed with progress; Toolbox → 下载管理 task list with progress bar,
  retry, open-with and confirmed delete. API 29+ stores into system
  `Download/ClashBrowser` (MediaStore); API 26–28 into the app-private external
  Download dir — no new permissions. Tasks persist; interrupted ones restore
  as failed/retryable. Downloads follow the in-app proxy and session cookies.
- File upload: `onShowFileChooser` opens the system SAF picker (multi-select)
  and returns results to `<input type="file">` pages.

## [1.13.2] — versionCode 20 (was 1.6.9)

### Fixed
- Import validation rejects proxy nodes pointing at the local mixed port
  (`127.0.0.1` / `localhost` / `::1` / `0.0.0.0` on port 7890), which would
  form a proxy self-loop once imported.
- `port` and `socks-port` are forced to 0 alongside `mixed-port: 7890` so an
  imported config cannot conflict with the WebView proxy listener.
- NOTICE: mihomo is GPL-3.0, not MIT; the attribution and license reference
  are corrected.

### Added
- JVM unit tests for the ad blocker rules, config validation and address
  parsing, executed by CI (`testDebugUnitTest`).
- Android lint step in CI with a committed baseline: existing findings are
  recorded, new lint errors fail the build.

## [1.13.1] — versionCode 19 (was 1.6.8)

### Fixed
- Proxy fail-open: the enabled preference is persisted only after the core is
  running and the WebView proxy override is applied; a failed enable attempt
  rolls the switch back and states clearly that traffic is direct.
- Cold-start and resume-to-foreground core failures roll back the enabled
  preference instead of leaving the switch on while WebView runs direct.
- The proxy switch and the top banner reflect the actual running state (core
  stopped / proxy not ready) instead of only the stored intent.

## [1.13.0] — versionCode 18 (was 1.6.7)

### Added
- "Open new pages in a new tab" option in Toolbox → More; pages that open a new
  window no longer replace the current tab by default.
- Folder picker when bookmarking the current page (root or any user folder,
  shown with hierarchy).
- "Move" action in the bookmark long-press menu to move a bookmark into any
  folder.

## [1.12.0] (was 1.6.6)

### Added
- Long-press context menu on clickable elements: open in background, open in
  new tab, view image, save image, download image, share image, image gallery
  mode (page image grid + fullscreen viewer), page info, mark ad domain,
  copy link text, scan QR code, copy link, share.
- Context menu items are generated from the hit-test result and link/image
  context.
- Image requests use the same in-app proxy as WebView.

## [1.11.1] (was 1.6.5)

### Fixed
- DRM-encrypted video playback: grant `RESOURCE_PROTECTED_MEDIA_ID` on the main
  thread, enable first- and third-party cookies, use a standard Android Chrome
  UA (removing the embedded WebView marker), allow-list DRM license providers
  and media CDNs in the ad blocker, and add a 4-second EME probe timeout so
  players fall back to unencrypted streams instead of hanging.

## [1.11.0] (was 1.6.4)

### Fixed / Added
- Fixed "this media type is not supported / enable DRM" errors on some online
  video sites by quickly falling back to unencrypted streams when the WebView
  DRM probe hangs.
- Fullscreen playback for web videos.
- Media streams and segments are no longer blocked by the ad blocker.
- Bottom bar changed to back / forward / home / tabs / toolbox; reload moved
  next to the proxy status dot and hides while the address bar is expanded.

## [1.10.0] (was 1.6.3)

### Added
- Tapping the address bar selects its whole content, so typing replaces it.
- Multi-level bookmark folders: create, rename and nest subfolders; importing a
  bookmarks file preserves the folder hierarchy.
- Long-press menu for bookmarks and folders: rename, edit URL, delete,
  multi-select, delete all bookmarks. All destructive actions require a
  confirmation dialog.

## [1.9.0] (was 1.6.2)

### Added
- Tapping a blank area of the page dismisses the address-bar input without
  changing the current page.
- Bookmark import/export: export to a standard bookmarks file, import from
  files exported by Via, Chrome, Firefox and others, deduplicated automatically.

## [1.8.0] (was 1.6.1)

### Added
- Tapping a blank area of the home page dismisses the keyboard.
- Home page hides the address bar and shows only the proxy status dot; the
  address bar returns once a page is open.
- Long-press a home shortcut to edit or remove it, change its letter, URL and
  display name.

## [1.7.0] (was 1.6.0)

### Added
- New local home page: browser logo, search box and up to 8 selectable
  circular bookmark shortcuts; new tabs and the home button open it.
- Address bar expands with an animation when focused and shows a clear button.
- Toolbox → More page with an "restore pages after unexpected exit" switch.
- Open-source statement on the About page.

## [1.6.2] (was 1.4.5)

### Changed
- Configuration page redesigned as profile cards with name, traffic usage,
  expiry date, usage bar and update time; tap to switch, menu to update/delete.
- App icon changed to a grey/white palette.
- Removed the mode-switch hint text and the bottom sheet.

## [1.6.1] (was 1.4.4)

### Changed
- Proxy mode semantics: Global mode routes all traffic through the `GLOBAL`
  selector (overriding subscription rules; a `DIRECT` GLOBAL selection is
  corrected to a real node). Rule mode follows subscription rules (direct for
  domestic, proxy for overseas). The node panel hides the `GLOBAL` group
  outside Global mode. Added mode descriptions.

## [1.6.0] (was 1.4.3)

### Added
- Secure keyboard hardening: focusing a password field rebuilds the input
  connection and re-shows the keyboard, so OEM secure keyboards appear on the
  first tap.
- Automatic session restore: after the process is killed, the next cold start
  asks whether to reopen the previous tabs (dialog follows the app theme).

## [1.5.0] (was 1.4.2)

### Added
- Light / Dark / Follow-system appearance with web page dark mode (tab history
  preserved).
- Background footprint reductions: pause web timers and rendering when the app
  is backgrounded, tighten the network idle connection pool, remove unused
  dependencies.

## [1.4.0] (was 1.4.1)

### Added
- Password fields are rewritten to the native password input type, enabling
  system secure keyboards (e.g. vivo).
- Bookmarks in the toolbox: bookmark the current page, search, open and delete.

## [1.3.1] (was 1.4)

### Fixed / Changed
- Fixed "core not running / configuration lost" after the app stayed in the
  background: restart the core silently on returning to foreground, auto-revive
  it when killed while running, and never reset configuration from a health
  check.
- Node list redesigned as compact grey/white cards with a light-blue highlight
  for the selected node; lightweight animations for toolbox sub-pages and group
  expansion.

## [1.3.0] (was 1.3)

### Fixed / Added
- Fixed proxy configuration being lost after exiting and reopening: stale
  process cleanup, atomic config writes with backup self-healing, automatic
  retry on startup failure.
- Incognito mode switch and browsing history in the toolbox.
- Password fields automatically request the IME private keyboard mode.

## [1.2.0] (was 1.2)

### Changed / Added
- Nodes shown as cards (flag / protocol / latency); policy groups can be
  expanded and collapsed.
- Toolbox added to the bottom bar; user-agent switching moved into the toolbox;
  About page added.

## [1.1.0] (was 1.1)

### Added
- Proxy master switch; Global / Rule / Direct mode switching.
- Default home page changed to Google.
- Long-press the tab button to open a new tab quickly.
- User-agent switch (mobile / desktop).

## [1.0.0] (was 1.0)

### Added
- Embedded mihomo core and Geo databases integrated automatically.
- Local WebView proxy without VPN permission.
- Ad/tracker blocking; multi-tab browsing; subscription import and YAML
  editing; node switching and batch latency tests; core logs.