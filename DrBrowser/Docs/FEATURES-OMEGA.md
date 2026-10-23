# DrBrowser OMEGA feature and limitation matrix

Status definitions:

- **Implemented/verified** — source compiled and the listed invariant ran in Windows CI.
- **Implemented/manual boundary** — real code exists, but the environment-dependent effect is not fully automatable.
- **Limited** — intentionally narrower than a mainstream browser; limitation is stated.
- **Not implemented** — absent; no UI claim should imply otherwise.

| Area | Status | Evidence / boundary |
|---|---|---|
| Web browsing/navigation | Implemented/verified | Live WebView2 startup, navigation, title and JS smoke assertions |
| Multi-tab lifecycle | Implemented/verified | Create/switch/close/reopen smoke; pin/mute/reorder code paths |
| Session restore | Implemented/verified | JSON round-trip self-test |
| Tab Sleep | Implemented/manual boundary | Real `TrySuspendAsync`/resume; timing depends on live tabs |
| Downloads | Implemented/verified | Real `DownloadStarting` loopback download with bytes/state check |
| History/bookmarks | Implemented/verified | SQLite CRUD/filter/import/export checks |
| Private window | Implemented/verified | Temporary profile/DB creation and deletion check |
| Multiple persistent profiles | **Not implemented** | One normal data profile; name/avatar/custom WebView folder only |
| Vault | Implemented/verified | DPAPI round-trip and SQLite ciphertext assertion; no autofill |
| DrSearch FTS5/BM25 | Implemented/verified | Phrase/ranking/dedupe/integration checks |
| Ethical crawler | Implemented/verified core | Bounds/robots/SSRF code; public-site behavior depends on network |
| Internet-scale independent index | **Not claimed/not implemented** | Personal bounded index only |
| DrMind Local | Implemented/verified | Deterministic transformations; explicitly not an LLM |
| DrMind Ollama | Implemented/manual boundary | Loopback validation/list/NDJSON streaming; user supplies model/service |
| OpenAI-compatible provider | Implemented/manual boundary | DPAPI key and client; CI has no user key |
| RAG/citations | Implemented/verified core | Sources come from local SearchIndex records |
| AI memory | Implemented/verified | Explicit SQLite add/context/delete; opt-in prompt inclusion |
| AI actions | Implemented/verified core | Two allowlisted, confirmation-gated actions; no arbitrary OS access |
| DrMusic local library | Implemented/verified core | Folder scan/categories/filter; smoke passes a valid WAV to real MediaPlayer and reports a skip on headless CI when Windows returns no-audio-device `0xC00D11BA`; audible output remains hardware-dependent |
| Playlists/favorites | Implemented/verified | SQLite create/add/remove/toggle/delete round-trip |
| Radio/catalog previews | Implemented/web boundary | Public URLs open in WebView2; not integrated with local transport controls; provider/network availability varies |
| Protected music downloads | **Not implemented** | No DRM bypass/downloader |
| Workspaces | Implemented/verified | SQLite CRUD, six presets and tab snapshot data |
| Notes/Tasks | Implemented/verified | SQLite CRUD/completion checks |
| DrTools | Implemented/verified | Parser/JSON/units/color/CSPRNG check |
| OMEGA sidebar | Implemented/verified | Internal page smoke routing; width is user-resizable |
| DrCommand | Implemented/verified | Registry/filter/route execution smoke |
| Performance | Implemented/manual boundary | Actual Process/GC/WebView values; CPU is sampled |
| Gaming Mode | Limited | Theme + aggressive tab sleep + links; no injection/FPS overlay/power plan |
| Themes | Implemented/verified | Ten centralized palettes counted in self-test |
| AdBlock | Limited | Network request filtering/custom lists/allowlist; no cosmetic engine |
| DrGuard | Implemented/manual boundary | URL rules and Defender invocation when available; not a safety guarantee |
| Backup/restore | Implemented/verified core | Snapshot/manifest/hash/composition tested; restore validates/integrity-checks |
| Installer | Implemented through CI | Inno Setup per-user script; unsigned |
| Chrome extensions | **Not implemented** | WebView2 platform limitation |
| Sync/account cloud | **Not implemented** | No hidden telemetry or cloud sync |
| Auto-update | **Not implemented** | Update manually with portable EXE/setup |
| Digital signature | **Not implemented** | SHA-256/provenance provided; SmartScreen can warn |

Historical [`FEATURES-2.1.md`](FEATURES-2.1.md) describes the earlier 2.1 baseline and is retained only for history. This matrix is authoritative for OMEGA 3.0.
