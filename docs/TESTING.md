# WinLauncher 1.0.0 — verification report

This file distinguishes code implementation, automated tests, and real end-to-end verification. A successful compilation does not prove Microsoft login, Minecraft rendering, mod compatibility, Discord presence or installer execution.

## Environment

Development: Linux x64 sandbox, Node.js 22. Rust and Windows SDK are not installed locally. Network egress is restricted to GitHub, npm and PyPI. Native builds are delegated to the Windows workflow on this branch. No Microsoft Application ID, licensed account, CurseForge key or Discord Application ID is bundled.

## Observed results (2026-10-21 UTC)

Evidence: [Windows run 39651203633](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39651203633), source commit `5638747e949cc896ab82596fe314878a0cb6df38`.

- **PASSED**: TypeScript + production Vite build; 10/10 frontend unit tests.
- **PASSED**: 12/12 Playwright browser acceptance tests (17.3 seconds).
- **PASSED**: axe-core audit of all nine main pages, default theme, Chromium 1440×1000: zero reported violations. This does not certify every theme, screen reader or native WebView2.
- **PASSED**: 21/21 Windows Rust unit tests.
- **PASSED**: genuine MSVC Windows x64 executable and supported Tauri NSIS packaging.
- **PASSED**: 22 real native backend acceptance scenarios, on Windows 11 (26100) x64.
- **NOT TESTED**: rendered GUI acceptance via WebView2 CDP. The real EXE remained running, but the harness could not attach. A green packaging workflow is not a successful GUI acceptance result.

The API bridge delivered `BACKEND-SMOKE.json/.md`, `NATIVE-SMOKE.json/.md`, `rust-test.log` and `backend-smoke.log`; their lengths and SHA256 hashes were checked before reading. The backend harness uses the production engine with real publisher HTTPS APIs, downloaded files, hash verification, real Java processes and SQLite. Only the UI event sink is omitted; no game, catalog, download or credential is simulated.

Java 8/17/21/25, a real disabled-project version update and Iris required dependencies all passed in this run. The NSIS acceptance step returned a failure, despite the workflow being green because acceptance steps are non-blocking. Its full diagnosis is being retrieved separately; it is not reported as a successful install. The GUI harness is still NOT TESTED. Results apply to the exact recorded commit.

## Requested end-to-end checks

| Check | Status | Verified scope / remaining limits |
|---|---|---|
| Install Minecraft Vanilla | PASSED | Real 1.21.4 client, libraries, native archives, assets and repeated cached-file hash verification. Not every historical/snapshot version. |
| Real licensed Minecraft launch | NOT TESTED | Requires a Microsoft Application ID, licensed account and rendered game session. |
| Automatic Java installation | PASSED | Genuine Adoptium Java 8u504, 17.0.20.1, 21.0.12.1 and 25.0.4.1 x64: SHA256 archives, extraction and actual `java -version` for all four. |
| Microsoft authentication / refresh | NOT TESTED | No registered Application ID or licensed account supplied. |
| Reject unlicensed launch | PASSED | Backend refused launch without an authenticated account, before spawning a game process. |
| Fabric installation | PASSED | Real Fabric 0.19.5 for Minecraft 1.21.4, official metadata and Maven files. Game rendering NOT TESTED. |
| Forge installation | PASSED | Real official installer for 1.21.4, verified JAR and resulting libraries. Game rendering NOT TESTED. |
| NeoForge installation | PASSED | Real official installer for 1.21.4 and verified resulting libraries. Game rendering NOT TESTED. |
| Quilt installation | PASSED | Real 1.21.4 loader metadata and libraries. Game rendering NOT TESTED. |
| Modrinth API / installation | PASSED | Genuine API search and compatible Sodium installation, hashes and actual JAR checked. |
| Mod toggle / removal | PASSED | Real files disabled/re-enabled and moved to safe backup storage. |
| Mod updates / required dependencies | PASSED | Downloaded an older compatible Sodium release, disabled it, updated to the latest real release without enabling it, then re-enabled. Separately installed Iris and its real required dependency. |
| Modrinth .mrpack export/import | PASSED | Exported actual installed content as verified references and reimported into a new isolated profile. |
| CurseForge API / ZIP modpack | NOT TESTED | Requires a valid API key. Distribution restrictions are not bypassed. |
| Discord Rich Presence | NOT TESTED | Requires Application ID, registered asset and running Discord Desktop. Protocol unit tests are not end-to-end Discord verification. |
| RAM affects actual game JVM | NOT TESTED | Argument tests do not substitute for inspecting a licensed Minecraft process. |
| Data-directory migration | PASSED | Real downloaded game/runtime/mod data copied, SHA256 verified, original retained. Cross-volume/user-world/failure-mode cases NOT TESTED. |
| Native settings persistence | PASSED | Saved settings in SQLite and verified after reopening the backend. Full GUI process restart NOT TESTED. |
| Hardware information | PASSED | Actual Windows CPU, RAM and free disk checks; GPU query executed. |
| User-world backup / restoration | NOT TESTED | No licensed game world created for this session. Filesystem safety checks are separate. |
| Full application restart | NOT TESTED | Requires rendered GUI acceptance. |
| NSIS installer execution | FAILED | Acceptance step returned exit code 1. Packaging succeeded. Dedicated diagnostics/retest pending; interactive installation remains NOT TESTED. |

## Automated tests

The executed results above are distinct from integration scenarios below. Source tests cover progress from real counters, path traversal and reserved Windows names, allowed HTTPS hosts, token redaction, file integrity, quoted legacy arguments, Maven coordinates, Mojang rules, Java versions, JVM memory arguments, theme data, and session statistics. UI tests use the explicitly labelled browser preview, not mocked successful native operations.

## Manual Windows acceptance checklist

1. Install with a standard Windows user. Check WebView2 bootstrap, Start Menu shortcut and launch.
2. Configure Microsoft Application ID. Test successful device login, cancellation, expiry, refresh, unlicensed account rejection and account switching. Inspect Credential Manager and SQLite to ensure tokens are not in SQLite/logs.
3. Create isolated Vanilla profiles for 1.8.9, 1.12.2, 1.16.5, 1.18.2, 1.20.1 and current release. Install and launch each. Verify Java major, -Xms/-Xmx, PID, logs, normal close and statistics. Test invalid custom Java.
4. Interrupt network access, corrupt an asset, cancel a download, run out of disk space. Check that unverified downloads are never promoted to game files.
5. Test each loader on a supported version. For Forge/NeoForge inspect actual official installer output. Do not label untested historical versions supported without testing.
6. Install mods with dependencies from both providers. Test incompatible loader, changed local file, name collision, disabled mod, rollback and missing dependencies. Test CurseForge downloadUrl=null rejection.
7. Import a real .mrpack and CurseForge ZIP into new instances, including a malicious traversal ZIP. Export and reimport a Modrinth-only pack. Original worlds must not change.
8. Configure Discord Desktop and Application ID. Test launch, downloads, browsing, playing, every privacy toggle, disabling and closing to tray. Presence name is the application name configured in Discord, not dynamically renamed to Minecraft.
9. Test root migration to another volume. Verify SHA256 and retention of the old directory. Test backup/restore into a new world folder.
10. Switch every theme/language, turn effects off, restart. Verify settings survive in SQLite. Check high DPI and keyboard navigation.

## Known limits (not disguised as completed features)

- Windows runtime integrations are implemented but require the acceptance checks above.
- No unlicensed/offline/cracked launch.
- Legacy Forge installers without headless support can fail with an actionable error.
- Independent overrides for every storage subdirectory are not supported; root migration and per-profile Java/RAM are supported.
- Datapack projects can be discovered/opened, but automatic installation into a selected world is not implemented.
- Existing modpacks are not automatically replaced in-place. Import a newer pack into a new profile; use safe world backups for migration.
- Exports intentionally exclude user jars, CurseForge files, worlds, configs and credentials. Only verified URL-backed Modrinth/.mrpack content is exported.
- Launcher release checks are real. Silent self-update is disabled until a signed update channel is published.
- The full application has Russian copy; navigation and primary controls support English/Ukrainian. Some technical help and backend errors currently remain Russian.
- No Windows code-signing certificate is bundled. Windows SmartScreen may warn about an unsigned executable.
