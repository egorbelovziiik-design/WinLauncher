# DrBrowser OMEGA 3.0

DrBrowser OMEGA is a real Windows 10/11 x64 browser application built on **.NET 10, WPF, MVVM, Microsoft Edge WebView2 and SQLite**. Web pages are rendered by separate WebView2 controls; browser state is persisted by actual services, not demo data or notification-only buttons.

## Download and integrity

| Artifact | Size | SHA-256 |
|---|---:|---|
| [`../dist/DrBrowser.exe`](../dist/DrBrowser.exe) | 68,195,329 bytes | `36A8DF2494794AE3150B04A2DC936BCE2878DFB9344CC2253FA5840F422EB28A` |
| [`../dist/DrBrowserSetup.exe`](../dist/DrBrowserSetup.exe) | 62,422,958 bytes | `1E8FC15937605EE9BA8DFD2BCA1BE7F8E944164AF57A6044EAF287520DCFDEC6` |
| `../dist/*.sha256.txt` | — | Sidecars generated from the exact CI outputs |

Verify a downloaded file:

```powershell
Get-FileHash .\DrBrowser.exe -Algorithm SHA256
Get-Content .\DrBrowser.exe.sha256.txt
```

The project is not Authenticode-signed, so SmartScreen may warn. The self-contained EXE does not need a separate .NET installation. **Microsoft Edge WebView2 Runtime is required** for web content: <https://go.microsoft.com/fwlink/p/?LinkId=2124703>.

See [INSTALL.md](Docs/INSTALL.md) for install, update, uninstall and source-build instructions.

## OMEGA modules

### Browser core

- One real WebView2 per web tab, created lazily; back/forward/reload/stop/home and URL-or-search parsing.
- Create, close, restore, duplicate, reorder, pin and mute tabs; session restore and configurable tab sleeping through `TrySuspendAsync`.
- Downloads use `CoreWebView2DownloadOperation`: actual bytes, progress, speed, cancel, open file/folder and SQLite history.
- SQLite history/bookmarks; bookmark bar; Netscape HTML import/export; suggestions from local data.
- Page find, zoom, screenshot, Print-to-PDF, reader mode, Picture-in-Picture request, F11 and F12 DevTools.
- Private **window** with a temporary WebView2 user-data folder and temporary SQLite database, deleted on close.
- Site permission prompts, crash reporting/recovery, new-window handling and fullscreen events.

### DrSearch

DrSearch is not a renamed third-party results page. It owns a SQLite **FTS5** index and uses **BM25** ranking with canonical URL normalization, deduplication, snippets, quality/language metadata and index statistics.

The optional crawler is deliberately bounded. It honors robots rules, crawl delay, configured page/depth/domain limits, `noindex`, redirects and content-size limits; it rejects loopback/private/link-local destinations to mitigate SSRF. It does not bypass authentication, CAPTCHA or robots policy and does not claim to index the whole internet. Optional public-web results are visibly separated from local DrSearch results.

### DrMind

- Providers: deterministic offline Local engine, loopback-only **Ollama**, and user-configured OpenAI-compatible HTTPS endpoint.
- Nine modes: Chat, Search, Research, Coding, Study, Writing, Translation, Browser and Music.
- Streaming Ollama output, stop/regenerate, bounded conversation history and configurable model/context/temperature.
- Optional active-page context; page text is labeled untrusted in the system prompt.
- Optional DrSearch RAG with numbered source URLs; the UI does not invent sources when none exist.
- Explicit SQLite memory: nothing is inferred or stored automatically. The user must add each fact and separately enable prompt inclusion. Cloud inclusion is disclosed beside the switch.
- Controlled action allowlist: save the last response to Notes or open its first HTTPS URL. Both require an explicit confirmation; DrMind has no arbitrary shell or filesystem tool.
- OpenAI-compatible API keys are stored with Windows DPAPI; Ollama URLs must resolve to loopback. No model is bundled or downloaded automatically.

### DrMusic

- Reads audio only from a folder chosen by the user; supports common formats exposed by WPF Media Foundation (`mp3`, `wav`, `m4a`, `aac`, `flac`, `ogg`, `wma`, `aiff`, subject to installed codecs).
- Real local playback, queue, previous/next, pause, stop, seek, volume, shuffle/repeat and persistent mini-player.
- Folder-based categories, title/artist parsing, filter, **SQLite playlists and favorites**; removing a playlist never deletes audio files.
- Eight public radio stream entries with LIVE state and disabled seeking.
- iTunes/Deezer public catalog search plays only provider-supplied previews; full tracks open on the provider page. Yandex Music opens its normal web search. There is no DRM bypass or protected-media downloader.

### Productivity and OMEGA shell

- Resizable left sidebar with DrMind, DrSearch, DrMusic, Workspaces, Notes, Tasks, DrTools, Gaming, Performance, Downloads and Settings.
- Workspaces persist name/color and a real snapshot of open tab URL/title/pin state. Six initial presets are seeded; replacing tabs requires confirmation.
- Notes and Tasks provide SQLite create/update/delete/completion operations; destructive operations require confirmation where appropriate.
- DrTools: safe expression parser (no script `eval`), JSON format/minify, unit and temperature converter, CSPRNG password generator, text cleanup and HEX/RGB conversion.
- Performance page reports the actual DrBrowser process working set, managed heap, sampled CPU use, tab count, WebView2 process count and runtime version.
- Gaming Mode applies the Gaming theme and aggressive browser tab sleep; Gaming offers ordinary HTTPS links and does not manipulate game processes.
- DrCommand (`Ctrl+Shift+P`) searches 23 registered actions and executes the selected command.

### Privacy, security, Vault and backup

- DrGuard URL rules and optional Microsoft Defender command-line scan for completed downloads where Defender is available.
- Request-level AdBlock with built-in rules, custom network rules, EasyList import URL, per-site allowlist and counters. Cosmetic element hiding is not claimed.
- Vault passwords are DPAPI-encrypted before SQLite. No website password capture or autofill is performed.
- Backups use SQLite Online Backup API, a manifest and SHA-256; restore rejects unexpected paths/files and runs `PRAGMA integrity_check`. API keys, WebView2 cookies/cache and secret files are excluded. Vault rows stay DPAPI ciphertext and are portable only to the same Windows account.
- No hidden telemetry. Network access occurs only for pages/features the user invokes (sites, selected search/catalog/weather source, filter update, configured AI provider).

### Themes and accessibility

Ten centralized palettes are included: Omega Dark, Omega Light, Neon Cyber, AMOLED Black, Purple Aurora, Blue Ocean, Pink Sakura, Green Matrix, Orange Sunset and Gaming. Accent presets, custom accent, reduced animations and three Tab Sleep presets are persisted.

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+T`, `Ctrl+W`, `Ctrl+Shift+T` | New, close, restore tab |
| `Ctrl+L`, `Ctrl+R`, `Ctrl+F` | Address, reload, page find |
| `Ctrl+H`, `Ctrl+J`, `Ctrl+D` | History, downloads, bookmark |
| `Ctrl+Shift+N` | New private window |
| `Ctrl+P` | Save current web page as PDF |
| `Ctrl+Shift+P` | DrCommand palette |
| `Ctrl+Shift+A`, `Ctrl+Shift+S`, `Ctrl+Shift+M` | DrMind, DrSearch, DrMusic |
| `Ctrl+Shift+G` | DrGuard |
| `Ctrl+Alt+Space/Left/Right` | Music play/pause, previous, next |
| `Ctrl+Tab`, `Ctrl+Shift+Tab` | Next/previous tab |
| `Ctrl++`, `Ctrl+-`, `Ctrl+0` | Zoom |
| `F11`, `F12` | Fullscreen, DevTools |
| `Alt+Left`, `Alt+Right` | Back/forward |

Shortcuts are bridged from WebView2 `AcceleratorKeyPressed`, so they also work while a page has focus.

## Data locations

Normal mode uses `%AppData%\DrBrowser`:

| Data | Location |
|---|---|
| Settings | `settings.json` |
| History, bookmarks, Vault ciphertext, downloads, Workspaces, Notes, Tasks, playlists, AI memory, DrSearch | `drbrowser.db` (SQLite/WAL) |
| OpenAI-compatible key | `secrets\ai.key` (DPAPI) |
| WebView2 profile | `profile\default` or configured folder |
| Session | `session.json` |
| Filters, logs, screenshots | `filters\`, `logs\`, `screenshots\` |

Private windows use GUID-named temporary profile/database paths and attempt recursive deletion at close.

## Build and test

```powershell
dotnet restore ..\DrBrowserOmega.sln
dotnet build ..\DrBrowserOmega.sln -c Release
dotnet publish DrBrowser.csproj -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:EnableCompressionInSingleFile=true -o ..\dist

..\dist\DrBrowser.exe --selftest
..\dist\DrBrowser.exe --smoke
```

Final GitHub Actions run [40033545801](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/40033545801) succeeded: Release build, self-contained publish, Inno Setup compilation, checksums, all **35/35** logic checks and all **12/12** live-window/WebView2 smoke checks passed with **0 failures and 0 skips**. Exact reports are committed under `screenshots/`; verified binaries are committed under top-level `dist/` and attached to the [OMEGA release](https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/drbrowser-latest).

## Known limitations

- WebView2 Runtime is an external dependency; without it, local pages still open but websites cannot render.
- Binaries are unsigned. This is not equivalent to malware, but users should verify hashes and provenance.
- There is one persistent normal-mode data profile in this release. Profile name/avatar and WebView2 data-folder customization exist, plus isolated private windows; a full multi-profile switcher with separate bookmarks/settings is **not** implemented.
- Chrome Web Store extensions and arbitrary Chromium extensions are not supported by WebView2.
- DRM/Widevine behavior is not guaranteed. DrBrowser does not bypass DRM.
- AdBlock implements network filtering, not a full cosmetic-filter engine; service-worker behavior is constrained by WebView2 events.
- Vault intentionally has no autofill/import from websites. Clipboard contents are governed by Windows after a user copies a value.
- DrMind Local is deterministic text tooling, not an LLM. Ollama models and cloud access must be supplied/configured by the user.
- DrSearch is a personal bounded index, not an internet-scale search engine. Public results depend on external providers.
- Radio/catalog/weather/public-web features depend on network availability and their providers. No availability guarantee is made.
- WPF `MediaPlayer` codec support depends partly on Windows Media Foundation.
- Gaming Mode changes DrBrowser behavior/theme only; there is no FPS overlay, game injection or OS power-plan manipulation.
- Performance CPU is a sampled process estimate; the first sample is a baseline, not an instantaneous value.
- Backup does not contain API keys, WebView2 cookies/cache or a portable plaintext Vault. DPAPI ciphertext normally restores only for the same Windows account.
- CI window screenshots cannot reliably capture WebView2’s child HWND pixels; engine execution is instead asserted through navigation, document title and JavaScript.

See [FEATURES-OMEGA.md](Docs/FEATURES-OMEGA.md) for a status matrix and [SECURITY.md](Docs/SECURITY.md) for the threat boundaries.
