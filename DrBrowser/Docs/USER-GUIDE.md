# DrBrowser OMEGA user guide

## First launch

1. Confirm **Settings → About** reports a WebView2 Runtime version.
2. Address-bar queries always open **DrSearch**. There is no Google or other third-party default-engine selector. Under **Settings → Search**, the optional external-index lookup is off by default and remains separate from DrSearch results.
3. Review **Settings → Privacy/Security** before enabling remote filter updates or cloud AI.
4. Optionally select a local music folder. DrMusic never scans folders you did not choose.
5. Create a backup after configuring important local data.

## Browsing and tabs

Type a URL or search phrase in the address bar. DrBrowser accepts normal HTTP/HTTPS URLs and explicitly supported internal routes. Dangerous page-entered schemes are rejected.

- Use the tab context menu to duplicate, pin, mute, close or close other tabs.
- Drag a tab to reorder it.
- Closed tabs can be restored with `Ctrl+Shift+T`.
- Pinned, audible and actively downloading tabs are protected from automatic Tab Sleep.
- Select Off/Balanced/Aggressive under **Settings → Performance**.

Private browsing is a separate window (`Ctrl+Shift+N`) with temporary WebView2 and SQLite storage. It does not hide IP address, prevent browser fingerprinting or make network traffic anonymous.

## OMEGA sidebar and DrCommand

The left sidebar opens local modules without pretending they are web pages. Drag its right edge to resize it. Use `Ctrl+Shift+P` to open DrCommand, type part of an action name, choose with arrow keys, and press Enter.

## Workspaces

A Workspace stores a snapshot of current tabs: URL, title and pinned state.

1. Open **Workspaces** from the sidebar.
2. Enter a name/color and choose **Save current tabs**.
3. Choose **Open** on a card.
4. Confirm replacement of the current tabs.

Six first-run presets demonstrate useful tab sets. They are ordinary editable SQLite records, not hard-coded navigation after seeding.

## Notes and Tasks

Notes have title/body fields and explicit Save/Delete actions. Tasks have completion state and deletion. Records live in local SQLite. Destructive actions use confirmations where loss would be surprising.

## DrTools

- **Calculator** parses numbers, parentheses, operators and selected math functions; it does not execute script code.
- **JSON** formats or minifies valid JSON and returns parse errors for invalid input.
- **Units** converts only compatible categories.
- **Passwords** uses `RandomNumberGenerator`; ambiguous characters are omitted.
- **Text** changes case or collapses whitespace locally.
- **Color** validates HEX and returns RGB.

These tools do not send input to a server.

## DrSearch

### Search an existing local index

Open DrSearch or type a non-URL query in the address bar. DrSearch—not Google—runs BM25 ranking over its SQLite FTS5 corpus, then adds matching local bookmarks/history. Indexed results include canonical URL, title and snippet.

### Add content with the crawler

1. Set a public HTTP/HTTPS seed URL.
2. Configure allowed/blocked domains, page count, depth and delay.
3. Start crawl and watch progress/status.

The crawler can refuse a URL due to robots policy, private address resolution, unsupported content/path, redirects, `noindex`, size or configured bounds. This is expected security/ethics behavior; there is no bypass switch.

Optional external-index lookup is disabled by default. If you deliberately enable it in Settings, DrSearch still does not send a normal query automatically: press the separate external-index button. Those rows name their external source and are never represented as DrSearch-indexed documents.

## DrMind

### Provider selection

- **Local**: offline deterministic summaries/keywords/stats/code/text tools. It is not an LLM.
- **Ollama**: the endpoint must be loopback. Install Ollama and a model independently.
- **OpenAI-compatible**: set HTTPS base URL/model and save an API key. The key is DPAPI-protected.

### Context and citations

- **Use active page** sends bounded page text to the selected model; page content is treated as untrusted context.
- **Ask with DrSearch** retrieves local indexed snippets and appends numbered real URLs.
- Search/Research modes enable index use appropriate to the mode.

Always verify model output. A citation means that a DrSearch record was supplied, not that every generated statement is true.

### Explicit memory

1. Type a preference/fact in the memory field and select **Remember**.
2. Enable **Use explicit memory** separately.
3. Delete individual entries or confirm **Clear all**.

No chat is automatically converted to memory. If a cloud provider is active, enabled memory is included in its prompt as disclosed in the UI.

### Controlled actions

DrMind can only offer two application actions: save the last answer to Notes or open the first HTTPS URL in it. Each displays an explicit confirmation. It cannot execute shell commands, install software or freely edit files.

## DrMusic

1. Select a folder containing local audio. Subfolders become categories.
2. Select a track to play; use queue controls, seek, volume, shuffle and repeat.
3. Create a playlist in the left pane, select it, then use `+` beside a track.
4. Use the heart to toggle Favorites; `−` removes from the open playlist without deleting the file.
5. Radio entries are normal public links marked LIVE. Clicking one opens it in WebView2; it does not enter the local mini-player queue.
6. Catalog preview and provider-page buttons open lawful provider URLs in WebView2; DrBrowser does not download protected tracks.

Local codec availability depends on Windows. Remote availability depends on the provider. DrBrowser deliberately leaves .NET 10's hardened network-credential policy enabled, so integrated `MediaPlayer` controls are local-file-only.

## Gaming and Performance

Gaming Mode selects the Gaming palette and Aggressive Tab Sleep. It does not inject into games or alter the OS power plan. Quick links are standard HTTPS navigation.

Performance reports DrBrowser process/GC/WebView2 values. Press Refresh for a new sample. The first CPU sample establishes the timing baseline.

## Vault

Vault records site label, username and a DPAPI-encrypted password. Add/copy/delete are explicit. There is no automatic page capture or autofill. Decrypted values exist in process memory while loaded and enter the Windows clipboard only after the user presses Copy.

## Backup and restore

Open **Settings → Backup**.

- **Create** writes a consistent SQLite snapshot, settings and manifest to `.drbackup`.
- API keys, WebView2 cookies/cache and secret files are excluded.
- **Restore** validates paths, file allowlist, manifest, SHA-256, settings and SQLite integrity; then asks for confirmation and closes the app after replacement.
- Vault ciphertext is bound to the originating Windows user through DPAPI.

## Themes

Choose among ten palettes in Settings. Selecting a palette applies its designed accent; selecting a custom accent reapplies the current palette with that accent. Reduced animations is persisted. Theme changes are centralized through `ThemeManager`, rather than page-specific hard-coded brushes.

## Data and reset

See the data table in the main README. To reset everything, close every DrBrowser window and remove `%AppData%\DrBrowser`. Back up first; deletion is irreversible.
