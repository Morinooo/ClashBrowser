# Changelog

All notable user-visible changes to ClashBrowser. This file is derived from the
in-app changelog shipped with the app; dates are not recorded there and are
therefore omitted. The project uses `versionName` / `versionCode` pairs
(`1.7.0` / `21` as of the latest release). There was no `1.5.x` release.

## [1.7.0] — versionCode 21

### Added
- Download manager: non-image downloads captured via `setDownloadListener`,
  streamed with progress; Toolbox → 下载管理 task list with progress bar,
  retry, open-with and confirmed delete. API 29+ stores into system
  `Download/ClashBrowser` (MediaStore); API 26–28 into the app-private external
  Download dir — no new permissions. Tasks persist; interrupted ones restore
  as failed/retryable. Downloads follow the in-app proxy and session cookies.
- File upload: `onShowFileChooser` opens the system SAF picker (multi-select)
  and returns results to `<input type="file">` pages.

## [1.6.9] — versionCode 20

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

## [1.6.8] — versionCode 19

### Fixed
- Proxy fail-open: the enabled preference is persisted only after the core is
  running and the WebView proxy override is applied; a failed enable attempt
  rolls the switch back and states clearly that traffic is direct.
- Cold-start and resume-to-foreground core failures roll back the enabled
  preference instead of leaving the switch on while WebView runs direct.
- The proxy switch and the top banner reflect the actual running state (core
  stopped / proxy not ready) instead of only the stored intent.

## [1.6.7] — versionCode 18

### Added
- "Open new pages in a new tab" option in Toolbox → More; pages that open a new
  window no longer replace the current tab by default.
- Folder picker when bookmarking the current page (root or any user folder,
  shown with hierarchy).
- "Move" action in the bookmark long-press menu to move a bookmark into any
  folder.

## [1.6.6]

### Added
- Long-press context menu on clickable elements: open in background, open in
  new tab, view image, save image, download image, share image, image gallery
  mode (page image grid + fullscreen viewer), page info, mark ad domain,
  copy link text, scan QR code, copy link, share.
- Context menu items are generated from the hit-test result and link/image
  context.
- Image requests use the same in-app proxy as WebView.

## [1.6.5]

### Fixed
- DRM-encrypted video playback: grant `RESOURCE_PROTECTED_MEDIA_ID` on the main
  thread, enable first- and third-party cookies, use a standard Android Chrome
  UA (removing the embedded WebView marker), allow-list DRM license providers
  and media CDNs in the ad blocker, and add a 4-second EME probe timeout so
  players fall back to unencrypted streams instead of hanging.

## [1.6.4]

### Fixed / Added
- Fixed "this media type is not supported / enable DRM" errors on some online
  video sites by quickly falling back to unencrypted streams when the WebView
  DRM probe hangs.
- Fullscreen playback for web videos.
- Media streams and segments are no longer blocked by the ad blocker.
- Bottom bar changed to back / forward / home / tabs / toolbox; reload moved
  next to the proxy status dot and hides while the address bar is expanded.

## [1.6.3]

### Added
- Tapping the address bar selects its whole content, so typing replaces it.
- Multi-level bookmark folders: create, rename and nest subfolders; importing a
  bookmarks file preserves the folder hierarchy.
- Long-press menu for bookmarks and folders: rename, edit URL, delete,
  multi-select, delete all bookmarks. All destructive actions require a
  confirmation dialog.

## [1.6.2]

### Added
- Tapping a blank area of the page dismisses the address-bar input without
  changing the current page.
- Bookmark import/export: export to a standard bookmarks file, import from
  files exported by Via, Chrome, Firefox and others, deduplicated automatically.

## [1.6.1]

### Added
- Tapping a blank area of the home page dismisses the keyboard.
- Home page hides the address bar and shows only the proxy status dot; the
  address bar returns once a page is open.
- Long-press a home shortcut to edit or remove it, change its letter, URL and
  display name.

## [1.6.0]

### Added
- New local home page: browser logo, search box and up to 8 selectable
  circular bookmark shortcuts; new tabs and the home button open it.
- Address bar expands with an animation when focused and shows a clear button.
- Toolbox → More page with an "restore pages after unexpected exit" switch.
- Open-source statement on the About page.

## [1.4.5]

### Changed
- Configuration page redesigned as profile cards with name, traffic usage,
  expiry date, usage bar and update time; tap to switch, menu to update/delete.
- App icon changed to a grey/white palette.
- Removed the mode-switch hint text and the bottom sheet.

## [1.4.4]

### Changed
- Proxy mode semantics: Global mode routes all traffic through the `GLOBAL`
  selector (overriding subscription rules; a `DIRECT` GLOBAL selection is
  corrected to a real node). Rule mode follows subscription rules (direct for
  domestic, proxy for overseas). The node panel hides the `GLOBAL` group
  outside Global mode. Added mode descriptions.

## [1.4.3]

### Added
- Secure keyboard hardening: focusing a password field rebuilds the input
  connection and re-shows the keyboard, so OEM secure keyboards appear on the
  first tap.
- Automatic session restore: after the process is killed, the next cold start
  asks whether to reopen the previous tabs (dialog follows the app theme).

## [1.4.2]

### Added
- Light / Dark / Follow-system appearance with web page dark mode (tab history
  preserved).
- Background footprint reductions: pause web timers and rendering when the app
  is backgrounded, tighten the network idle connection pool, remove unused
  dependencies.

## [1.4.1]

### Added
- Password fields are rewritten to the native password input type, enabling
  system secure keyboards (e.g. vivo).
- Bookmarks in the toolbox: bookmark the current page, search, open and delete.

## [1.4]

### Fixed / Changed
- Fixed "core not running / configuration lost" after the app stayed in the
  background: restart the core silently on returning to foreground, auto-revive
  it when killed while running, and never reset configuration from a health
  check.
- Node list redesigned as compact grey/white cards with a light-blue highlight
  for the selected node; lightweight animations for toolbox sub-pages and group
  expansion.

## [1.3]

### Fixed / Added
- Fixed proxy configuration being lost after exiting and reopening: stale
  process cleanup, atomic config writes with backup self-healing, automatic
  retry on startup failure.
- Incognito mode switch and browsing history in the toolbox.
- Password fields automatically request the IME private keyboard mode.

## [1.2]

### Changed / Added
- Nodes shown as cards (flag / protocol / latency); policy groups can be
  expanded and collapsed.
- Toolbox added to the bottom bar; user-agent switching moved into the toolbox;
  About page added.

## [1.1]

### Added
- Proxy master switch; Global / Rule / Direct mode switching.
- Default home page changed to Google.
- Long-press the tab button to open a new tab quickly.
- User-agent switch (mobile / desktop).

## [1.0]

### Added
- Embedded mihomo core and Geo databases integrated automatically.
- Local WebView proxy without VPN permission.
- Ad/tracker blocking; multi-tab browsing; subscription import and YAML
  editing; node switching and batch latency tests; core logs.
