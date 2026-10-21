# WinLauncher 1.0.0 — verification report

This file distinguishes code implementation, automated tests, and real end-to-end verification. A successful compilation does not prove Microsoft login, Minecraft rendering, mod compatibility, Discord presence or installer execution.

## Environment

Development: Linux x64 sandbox, Node.js 22. Rust and Windows SDK are not installed locally. Network egress is restricted to GitHub, npm and PyPI. Native builds are delegated to the Windows workflow on this branch. No Microsoft Application ID, licensed account, CurseForge key or Discord Application ID is bundled.

## Observed results (2026-10-21 UTC)

- **PASSED**: `npm run build` (TypeScript + production Vite build).
- **PASSED**: 10/10 frontend unit tests.
- **PASSED**: 12/12 Playwright browser acceptance tests, latest rerun 17.7 seconds.
- **PASSED**: axe-core automated audit of all nine main pages, default theme, Chromium 1440×1000: zero reported violations. This does not certify every theme, screen reader or native WebView2.
- **PASSED**: Windows Rust unit-test step for commit `e236a737654754bd8f9dcf4eb58ecf38aeeec72c`.
- **PASSED**: genuine MSVC Windows x64 executable and supported Tauri NSIS packaging for that commit: [Windows run 39645687272](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39645687272).
- **NOT TESTED**: GUI acceptance via WebView2 CDP on that runner. The harness could not connect; a green packaging workflow is not a successful GUI/native acceptance result.
- The latest source adds hardened Discord RPC, loader cache validation, mutation locking and native backend acceptance checks. **Its Windows verification is pending** until its own CI reports are read. Historical build results must not be attributed to untested changes.

The workflow writes `BACKEND-SMOKE.json/.md` from the real Windows Rust engine and `NATIVE-SMOKE.json/.md` from the compiled GUI application. Backend checks use real publisher downloads, hashes, Java processes and SQLite, with only the UI event sink omitted. Neither harness invents a licensed account or a rendered game session. Acceptance steps can fail independently of packaging; read their reports, not only the workflow badge.

## Requested end-to-end checks

| Check | Status | Reason / required procedure |
|---|---|---|
| Install Minecraft Vanilla | NOT TESTED | Requires access to Mojang metadata/content endpoints on Windows. |
| Real Minecraft launch | NOT TESTED | Requires Windows, real Microsoft authentication, Minecraft ownership and GPU/display. |
| Automatic Java installation | NOT TESTED | Adoptium and runtime asset download must be exercised on Windows. |
| Microsoft authentication | NOT TESTED | Requires a registered/approved public application and a licensed user account. |
| Fabric | NOT TESTED | Requires real loader metadata, Java and a rendered game session. |
| Forge | NOT TESTED | Uses the official verified installer; legacy GUI-only installers may fail in headless mode. |
| NeoForge | NOT TESTED | Requires official installer execution and game launch. |
| Quilt | NOT TESTED | Requires compatible version metadata and game launch. |
| Modrinth API | NOT TESTED | The sandbox cannot reach api.modrinth.com. No fixture catalog is shown to users. |
| CurseForge API | NOT TESTED | Requires an API key; denied distribution is not bypassed. |
| Install, update, toggle and remove mods | NOT TESTED | Requires compatible real projects and dependency graphs. |
| Import/export .mrpack and CurseForge ZIP | NOT TESTED | Requires real archives/files and API access; traversal unit tests are separate. |
| Discord Rich Presence | NOT TESTED | Requires a real Application ID, registered asset and running Discord Desktop. |
| RAM affects Java process | NOT TESTED | Argument unit tests do not substitute for inspecting an actual game JVM. |
| Data-directory migration | NOT TESTED | Must be tested across Windows disks with user data and insufficient disk space. |
| Theme persistence in Windows | NOT TESTED | Browser preference tests do not prove native SQLite persistence. |
| Application restart | NOT TESTED | Requires a Windows desktop session. |
| NSIS installer execution | NOT TESTED | Building an installer does not test its installation/uninstallation. |

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
