# DrBrowser OMEGA architecture

## Runtime stack

- **Presentation:** WPF/XAML on `net10.0-windows`, centralized dynamic brushes/styles.
- **Pattern:** MVVM with `ObservableObject`, `RelayCommand`, view-model composition and a narrow `IBrowserShell` for dialogs/window operations.
- **Web engine:** Microsoft Edge WebView2, one control per web tab and one environment per window/profile.
- **Persistence:** Microsoft.Data.Sqlite plus JSON for settings/session.
- **Windows security/media:** DPAPI (`ProtectedData`), Microsoft Defender CLI integration when available, WPF/Media Foundation `MediaPlayer`.
- **Packaging:** self-contained compressed single-file `win-x64`; optional Inno Setup per-user installer.

## Dependency direction

```text
Views / MainWindow
       │ bindings + shell callbacks
       ▼
ViewModels (Main, Search, Music, NewTab, Omega)
       │ orchestration only
       ▼
Services (browser/search/AI/music/productivity/security/backup)
       │
       ├── SQLite / JSON / DPAPI / filesystem
       ├── WebView2 / MediaPlayer / Defender
       └── explicitly configured HTTP endpoints
```

Code-behind is limited to presentation mechanics that WPF does not express cleanly: window chrome, focus/keyboard routing, drag/drop, popup item forwarding, slider gestures and visual screenshot capture. Data operations and feature logic remain in view-models/services.

## Important components

### Browser

- `BrowserTab` owns tab metadata and its `WebView2`/`CoreWebView2` references.
- `TabManager` owns tab collection, active tab and recently closed stack.
- `WebViewManager` creates the shared environment, initializes controls and wires navigation, request filtering, download, permission, accelerator, popup, process-failure and fullscreen events.
- `MainViewModel` is split across partial files by concern: core lifecycle/navigation, commands, data/settings, AI, Guard, shell/personas/music bar, command palette and backup.
- `MainWindow` caches WPF internal pages and switches between the internal host and WebView2 host by `TabKind`.

WebView2 initialization is lazy. Inactive eligible tabs call `TrySuspendAsync`; activation calls `ResumeAsync`. Controls are disposed when tabs close.

### Persistence

`Db` initializes an idempotent schema for browser records, Workspaces/Notes/Tasks, music playlists/favorites/tracks, AI conversations/memory and migration metadata. Services open short-lived SQLite connections; expensive reads/writes run off the UI thread. WAL and normal synchronous mode balance responsiveness and durability.

- `SettingsService`: atomic JSON replacement and corrupt-file recovery.
- `SessionService`: open-tab state independent from SQLite.
- `ProductivityService`: Notes/Tasks/Workspace CRUD and serialized tab snapshots.
- `MusicPlaylistService`: playlist/favorite mapping to persistent track records.
- `AiMemoryService`: explicit, bounded memory records only.
- `BackupService`: SQLite Online Backup API plus manifest/hash/strict restore validation.

Private windows construct a GUID-named temporary `Db` and WebView2 profile. They do not reuse the normal database.

### DrSearch

`UrlParser` routes every non-URL address-bar input to `drbrowser://search`; there is no third-party search-template fallback. `SearchIndexService` owns FTS5 setup, normalized document metadata and transactional dedupe/upsert. Queries are converted to bounded FTS syntax, ranked with BM25 and returned with SQLite snippets. `WebSearchService` is only an opt-in, explicitly labelled external-index adapter; it is disabled by default and never feeds rows into the DrSearch corpus.

`SearchCrawlerService` is deliberately separate. Its HTTP pipeline enforces:

1. HTTP/HTTPS only and public DNS addresses;
2. configured allow/block domains;
3. robots fetch/evaluation and per-host delay;
4. redirect, page, depth, response-size and HTML-content bounds;
5. canonical URL and `noindex` handling;
6. sanitized extraction before index insertion.

It is a personal index builder, not an unrestricted crawler.

### DrMind

`MainViewModel.Ai` builds bounded provider requests. `AIService` handles OpenAI-compatible chat and DPAPI secret access. `OllamaService` validates loopback roots, checks/list models, and parses NDJSON streaming output. `LocalAiEngine` provides deterministic offline transformations.

RAG retrieves from `SearchIndexService`; source lists are assembled from records actually returned. Page text and stored memory are explicitly described as untrusted context. Memory is opt-in and controlled actions are hard-coded allowlist commands with shell confirmation.

### DrMusic

`MusicService` scans a selected folder, builds a local-file queue and controls WPF `MediaPlayer`. `MusicViewModel` combines local library state, public preview catalog, playlists/favorites and UI commands. `MusicPlaylistService` persists mappings without altering source files. Internet-zone radio/preview links are delegated to WebView2 rather than weakening hardened .NET 10's default-credential zone policy; integrated transport controls therefore apply to local files only.

### OMEGA productivity

`OmegaViewModel` composes `ProductivityService` and `UtilityEngine`. It does not own raw SQLite code. Tab capture/restore arrives through delegates from `MainViewModel`, so Workspace persistence is independent from browser-shell details. The calculator is a recursive-descent parser, not dynamic evaluation.

## Internal routes

Internal routes are WPF pages, never privileged HTML:

- Browser: `newtab`, `history`, `downloads`, `bookmarks`, `passwords`, `settings`, `guard`.
- OMEGA: `search`, `mind`, `music[/search|/library|/playlists]`, `workspaces`, `notes`, `tasks`, `tools`, `gaming`, `performance`, `privacy`, `about`.

`UrlParser` is the single route/title/scheme registry. Pages receive the same `MainViewModel` data context.

## Threading

- UI collection mutations return to the WPF dispatcher through awaited continuations.
- SQLite/file scanning/backup work runs in `Task.Run` where appropriate.
- HTTP uses async I/O and cancellation tokens.
- Ollama streams update a bound observable message on the UI continuation.
- Timers sample music position, settings debounce and tab suspension without busy loops.

## Failure boundaries

Global dispatcher/domain/task handlers log unhandled failures. Feature services catch expected I/O/provider failures and expose actionable status. WebView2 process failures are surfaced per tab. Test modes return nonzero on any `FAIL`; environment-dependent inability may be recorded as `SKIP` with a reason.

## Extending OMEGA

1. Put pure state/data code in a service or module directory.
2. Expose UI state through a focused view-model rather than `MainWindow.xaml.cs`.
3. Add a route through `UrlParser` and `TabKind` only if it needs a page.
4. Add real self-test coverage for deterministic logic and smoke coverage for page wiring.
5. Document network/storage/security boundaries and avoid presenting planned behavior as implemented.
