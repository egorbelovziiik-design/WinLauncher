# DrBrowser OMEGA

A Windows 10/11 x64 browser platform built with **C# / .NET 10 / WPF / MVVM / WebView2 / SQLite**.
This repository contains the application source, deterministic Windows build workflow, executable/installer outputs, documentation, and real self-test/smoke-test evidence.

## Downloads

Verified release: <https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/drbrowser-latest>

- [`dist/DrBrowser.exe`](dist/DrBrowser.exe) — portable self-contained `win-x64`, 68,198,913 bytes, SHA-256 `F0A2E284B5ED3194B1BD4352409A9ED7FDE21DB0C2FB5F3DC55078A27C058A62`.
- [`dist/DrBrowserSetup.exe`](dist/DrBrowserSetup.exe) — per-user Inno Setup installer, 62,423,777 bytes, SHA-256 `FD60C7C5119587BB5818930AA6AC0B794EC3BE772249094D9918493AA215CAAC`.
- `dist/*.sha256.txt` — checksum sidecars from final GitHub Actions run [40036576617](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/40036576617).

Microsoft Edge **WebView2 Runtime** is required. Current Windows 11 and maintained Windows 10 installations usually already include it. The app and installer show an official Microsoft download link when it is absent.

> The binaries are not Authenticode-signed. Windows SmartScreen may therefore warn on first launch. Verify the SHA-256 sidecar and build provenance rather than bypassing security checks blindly.

## What is implemented

- Real multi-tab WebView2 browsing, navigation, session restore, pinned/muted/reordered tabs, tab sleep, downloads, history, bookmarks, private windows, page find, PDF, screenshots, reader mode and DevTools.
- **DrSearch**: clearly labeled local SQLite FTS5/BM25 index plus a bounded crawler that respects robots rules, delays, domain limits, `noindex`, redirect limits and SSRF protections.
- **DrMind**: deterministic offline tools, loopback-only Ollama, or an OpenAI-compatible endpoint; streaming/cancel/regenerate, nine modes, optional DrSearch RAG with citations, explicit local memory, and confirmation-gated actions.
- **DrMusic**: real local-file playback, categories, queue, shuffle/repeat/seek/volume, SQLite playlists/favorites and mini-player; public radio and legal 30-second preview links open in WebView2 without weakening .NET's network-credential policy.
- OMEGA sidebar, six Workspaces presets and tab snapshots, Notes, Tasks, DrTools, Gaming links/mode, real process performance readings, ten themes, command palette, DPAPI Vault, AdBlock/DrGuard and validated backups.

No button is intentionally represented as working when it is only a mock. See the documented [limitations](DrBrowser/README.md#known-limitations).

## Build

```powershell
git clone https://github.com/egorbelovziiik-design/WinLauncher.git
cd WinLauncher
dotnet restore DrBrowserOmega.sln
dotnet build DrBrowserOmega.sln -c Release
dotnet publish DrBrowser/DrBrowser.csproj -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:EnableCompressionInSingleFile=true -o dist
```

The repository pins .NET SDK `10.0.100` with feature-band roll-forward in [`global.json`](global.json).

## Documentation

- [Full product README](DrBrowser/README.md)
- [Installation and build](DrBrowser/Docs/INSTALL.md)
- [User guide](DrBrowser/Docs/USER-GUIDE.md)
- [Architecture](DrBrowser/Docs/ARCHITECTURE.md)
- [Security and privacy](DrBrowser/Docs/SECURITY.md)
- [Testing and latest verified run](DrBrowser/Docs/TESTING.md)
- [Feature/limitation matrix](DrBrowser/Docs/FEATURES-OMEGA.md)
