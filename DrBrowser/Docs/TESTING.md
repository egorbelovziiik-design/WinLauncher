# Testing and verification

DrBrowser contains executable checks inside the same published binary:

```powershell
.\dist\DrBrowser.exe --selftest
.\dist\DrBrowser.exe --smoke
```

Both write human-readable reports and return `1` when any check records `FAIL`. Environment-dependent inability (for example external network outage) is recorded as `SKIP`, not silently reported as a pass.

## Last verified source run

GitHub Actions run **40032753453** on `windows-latest` completed successfully for source commit `5129ead`:

- .NET 10 Release build: passed.
- Self-contained compressed single-file `win-x64` publish: passed.
- EXE existence/minimum-size/SHA-256 generation: passed.
- `--selftest`: **35 checks, zero failures**.
- `--smoke`: **12 checks, zero failures**.
- Live WebView2 initialization, document title and JavaScript execution: passed.
- Reports/screenshots/artifact upload: passed.

Run: <https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/40032753453>

The final packaging run (including Inno Setup and committed `dist/` outputs) supersedes this run when its reports are present in `DrBrowser/screenshots/`. Check `exit-codes.txt`, `selftest-report.txt` and `smoke-report.txt` rather than relying only on this document.

## Logic/self-test coverage (35 checks)

1. Application data directories and logger.
2. JSON settings round-trip.
3. URL/search/internal parsing and dangerous-scheme handling.
4. Search engine definitions including DrSearch.
5. DrSearch integration over local browser data.
6. SQLite FTS5/BM25, phrase query and deduplication.
7. Notes/Tasks/Workspaces SQLite CRUD and six seeded snapshots.
8. Explicit DrMind memory add/context/delete.
9. Backup creation/manifest/hash/composition and secret exclusion.
10. DrTools calculator/JSON/units/color/password/themes.
11. Music folder scan/category/file filtering.
12. SQLite music playlists/favorites create/add/remove/delete.
13. Radio catalog/LIVE state/non-seek behavior.
14. AI mode/prompt distinction.
15. Profile avatar presets.
16. DrSearch recent queries.
17. SQLite history/filter/most-visited/delete/clear.
18. Bookmark CRUD/search/Netscape import/export/dedupe.
19. Vault DPAPI round-trip and ciphertext-at-rest assertion.
20. CSPRNG password generation/strength.
21. AI missing-key behavior without network call.
22. AdBlock domain/path/allowlist/custom rule/counter behavior.
23. Download size/state/history.
24. Session tab/pin/active-index persistence.
25. Quick links and permission CRUD.
26. Private profile creation/deletion.
27. WebView2 Runtime detection.
28. Settings normalization/bounds.
29. DrGuard URL rules.
30. Defender argument construction/safety.
31. AI image attachment limits/encoding.
32. Deterministic Local AI transformations.
33. Public music catalog parser.
34. DrSearch external-result parser/labeling.
35. Error log write.

## Live smoke coverage (12 checks)

1. Window starts, initializes tabs and can be captured.
2. WebView2 starts, renders a controlled page and confirms title with `ExecuteScriptAsync`.
3. Tabs create/switch/close/reopen.
4. Internal Settings, History, Downloads, Bookmarks, Vault, DrSearch, DrMusic, Workspaces, Tools, Performance and New Tab pages activate and render.
5. DrCommand loads, filters to DrMusic and executes the route.
6. Settings tab count and Music/AI sections render.
7. Zoom, find bar, AI panel, profile popup and bookmark add/delete.
8. DrMusic mini-player and LIVE state.
9. Command/hotkey bridge including F11/Escape/F5.
10. Real external HTTPS navigation when CI network is available, with protocol/title/body assertions.
11. Real WebView2 `DownloadStarting` against a bounded loopback HTTP server; output bytes/state verified.
12. WebView2 page screenshot when rendering is available.

## CI pipeline

`.github/workflows/build-drbrowser.yml` performs:

1. checkout and .NET 10 setup;
2. restore/build/publish;
3. portable EXE and SHA verification;
4. Inno Setup installer build and SHA verification;
5. Runtime detection;
6. self-test and smoke process runs;
7. report/log/screenshot collection;
8. artifact upload;
9. explicit final failure if process exit/report content indicates a failure;
10. final branch/release publication only when enabled for the packaging run.

Individual report failures are emitted as GitHub error annotations with details.

## What CI does not prove

- Audible audio on every physical device or continued availability of third-party streams.
- Every Windows Media Foundation codec combination.
- DRM/Widevine compatibility.
- Camera/microphone/geolocation hardware behavior after a human grants permission.
- Long-running/large download reliability and cancellation at every timing point.
- Valid responses from a user’s private AI endpoint/key or installed Ollama model.
- Model-answer factual correctness.
- SmartScreen/antivirus reputation or Authenticode trust (artifacts are unsigned).
- Full WebView2 pixel capture: WebView2 uses a child HWND that WPF `RenderTargetBitmap` may not capture; engine execution is asserted separately.
- Complete security. Tests cover known invariants, not all attacks.

## Run locally

```powershell
$env:DRBROWSER_SHOTS = "$PWD\test-output"
.\dist\DrBrowser.exe --selftest
$self = $LASTEXITCODE
.\dist\DrBrowser.exe --smoke
$smoke = $LASTEXITCODE
"selftest=$self smoke=$smoke"
Get-Content .\test-output\selftest-report.txt
Get-Content .\test-output\smoke-report.txt
```

Do not publish reports containing private URLs or local paths without reviewing them.
