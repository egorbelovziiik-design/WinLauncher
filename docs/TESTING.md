# WinLauncher 1.0.0 — verification report

**Verified on 2026-10-21 UTC.** Implementation, compilation, real backend execution and a rendered licensed game are different claims. A scenario marked **NOT TESTED** is not a success.

## Update: explicit local offline account

The user subsequently requested a local nickname-based account without Microsoft login. This supersedes the original license-only account requirement **for the new, explicitly labelled offline account type only**. Microsoft ownership validation is unchanged for Microsoft accounts. A local account is not a verified license and does not authenticate to online-mode servers. Full network-disconnected launch and rendered local gameplay are NOT TESTED.

New checks cover Java-compatible offline UUID/name validation, backward-compatible Microsoft account deserialization, native SQLite creation/reopen, duplicate prevention, identity resolution without credentials and deletion. **PASSED** on Windows in [run 39661734616](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39661734616), application commit `4d3b5421eee487f15c26aebba80f9fe70e2b4836`: 23 Rust unit tests, 25 real backend checks, 2 NSIS checks. Browser acceptance: 13/13; frontend unit tests: 10/10; settled offline-dialog axe audit: zero violations. [Current evidence](verification/offline-accounts/) and the release TESTING.md contain these results. **The exact older results and hashes in the following baseline sections describe the previous build, not the new one.**

## Previous baseline: exact build and evidence

- Application source / executable build: `fd718db7b9714ef3c5fd0b8856aa185355d75f32`.
- Windows build: [Actions run 39654021540](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39654021540).
- Final installer retest, using the **same** build artifact: [Actions run 39655709172](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39655709172).
- Corrected installer harness: `3f9089162804ab0ab6474d1fd7386a24a7d1ff02`. Subsequent CI/documentation changes do not change application code.
- Machine: real Windows x64 GitHub-hosted `windows-latest` runner. The native hardware query reported `Windows 11 (26100)`, x86_64, 16,378 MB RAM. This is not a manual test on a physical Windows 10/11 desktop.
- Local UI verification: Node.js 22, Chromium, Linux x64. The live browser preview explicitly does **not** simulate native installation, authentication, catalogs or gameplay.
- No Microsoft Application ID, licensed account, CurseForge API key or Discord Application ID was supplied or bundled.

Raw final evidence is committed under [`docs/verification/`](verification/), and the executable delivery also contains `BACKEND-SMOKE`, `INSTALLER-SMOKE`, `NATIVE-SMOKE` reports, `BUILD-METADATA.json` and `SHA256SUMS.txt`.

## Results at a glance

| Check | Result |
|---|---|
| TypeScript / production Vite build | **PASSED** |
| Frontend unit tests | **PASSED — 10/10** |
| Playwright browser acceptance tests | **PASSED — 12/12**, latest run 17.3 seconds |
| axe-core, nine main pages, default theme, 1440×1000 | **PASSED — zero reported violations** |
| Windows Rust unit tests | **PASSED — 21/21** |
| Genuine MSVC x64 EXE and supported Tauri NSIS packaging | **PASSED** |
| Real native backend acceptance checks | **PASSED — 22 executed scenarios**; 7 additional scenarios explicitly NOT TESTED |
| Real silent NSIS installation / uninstallation | **PASSED — 2/2**; interactive installation NOT TESTED |
| Native GUI automation / rendered Minecraft session | **NOT TESTED** |
| npm production dependency audit | **PASSED — zero reported vulnerabilities** at verification time |

The native harness uses the **production Rust engine**, real HTTPS publisher APIs, actual downloads and hash checks, real Windows Java executables, SQLite and filesystem operations. Only its UI event sink is omitted. It does not mock catalogs, files, credentials, progress or a licensed game.

The GUI harness started the real release EXE, which remained running, but could not attach through WebView2 CDP. No GUI inspection or game-rendering result is inferred from process liveness. Acceptance steps are non-blocking in the packaging workflow: **a green workflow badge alone is not acceptance evidence**.

## Requested scenarios

| Scenario | Status | Verified scope / remaining limits |
|---|---|---|
| Official Minecraft versions API | PASSED | Real Mojang manifest parsed; 920 versions returned during this run, including release 26.3 and snapshot 26.4-snapshot-5. No fabricated version list. |
| Install Vanilla | PASSED | Real Minecraft 1.21.4 client, libraries, native archives and assets downloaded and verified. |
| Recheck installed game files | PASSED | Repeated verification against the actual cached files. Hash-tamper unit tests also passed. |
| Release / snapshot / legacy game execution | NOT TESTED | Manifest filtering is implemented, but not every version was installed or launched. |
| Automatic Java 8 / 17 / 21 / 25 | PASSED | Genuine Adoptium Java 8u504, 17.0.20.1, 21.0.12.1 and 25.0.4.1 x64; SHA256 archives, extraction and real `java -version` for all four. |
| Microsoft OAuth, refresh, multi-account and skins | NOT TESTED | Requires a registered/approved public Application ID and licensed user accounts. No passwords or secrets should be sent in chat. |
| Refuse launch without an authenticated licensed account | PASSED | Backend rejected the no-account launch before spawning a game process. No account was selected in this historical check. The newly added explicit local account is tested separately. |
| Rendered licensed Minecraft / PID / exit tracking / playtime | NOT TESTED | No licensed session was started. Implementation and unit tests are not a substitute for this check. |
| Fabric installation | PASSED | Real Fabric 0.19.5 for Minecraft 1.21.4, official metadata and Maven libraries. Game rendering NOT TESTED. |
| Forge installation | PASSED | Verified official installer JAR executed for Minecraft 1.21.4, resulting libraries verified. Game rendering NOT TESTED. |
| NeoForge installation | PASSED | Verified official installer executed for Minecraft 1.21.4 and resulting libraries checked. Game rendering NOT TESTED. |
| Quilt installation | PASSED | Official 1.21.4 loader metadata and libraries installed. Game rendering NOT TESTED. |
| Modrinth API and mod installation | PASSED | Real search, compatible Sodium version, SHA-verified JAR and persisted metadata. |
| Required mod dependencies | PASSED | Installed Iris plus its genuine required dependency: two real files downloaded and verified. Conflicts also have source validation/unit coverage. |
| Disable / enable / remove mods | PASSED | Actual files renamed and re-enabled; removal moved content to managed backup storage. |
| Update a disabled mod | PASSED | Installed an older real Sodium release, disabled it, updated to the latest compatible release without enabling it, then re-enabled. |
| CurseForge search, downloads, dependencies and updates | NOT TESTED | Requires a valid key. Pinned file validation is implemented; distribution restrictions are not bypassed. |
| Modrinth `.mrpack` export / import | PASSED | Exported actual installed content as verified references, then reimported into a new isolated profile. Worlds and credentials were not exported. |
| CurseForge ZIP modpack | NOT TESTED | No API key / representative keyed pack provided. |
| Resource packs / shader packs / selected-world datapacks | NOT TESTED | Content support exists for the first two; datapacks are website-only, not automatically installed into a world. |
| Real Discord Desktop Rich Presence | NOT TESTED | Needs a configured Application ID, registered image asset and Discord Desktop. READY/nonce/ACK protocol unit tests passed, but are not desktop presence verification. |
| RAM / resolution / fullscreen in an actual game JVM | NOT TESTED | JVM argument validation passed unit tests; no licensed game process was inspected. |
| Create isolated profile | PASSED | Genuine filesystem directories and SQLite records created. |
| Migrate data directory | PASSED | Real downloaded game/runtime/mod data copied, SHA256 checked, original retained. Cross-volume/user-world/insufficient-space cases NOT TESTED. |
| CPU / GPU / RAM / disk information | PASSED | Real Windows query executed; CPU, RAM and free disk fields validated. |
| Native preference persistence | PASSED | SQLite settings saved and verified after reopening the backend. Full GUI process restart NOT TESTED. |
| Themes / effects / language in browser preview | PASSED | Preview/apply separation, persistence, actual particle removal, EN/UK preferences and navigation checked. Every theme's native/high-DPI appearance is not certified. |
| Full application restart / tray / hotkeys on Windows | NOT TESTED | Browser Ctrl+K/navigation checks are separate from native desktop behavior. |
| World backup and restoration | NOT TESTED | No licensed user world created for this session. Archive traversal safety unit tests passed. |
| Native logs / notifications / screenshots during gameplay | NOT TESTED | Browser checks confirm empty state is honest, but no real gameplay capture was exercised. |
| Download interruption, cancellation, rate limiting and disk-full recovery | NOT TESTED | Implemented, but these full fault-injection scenarios were not executed. |
| Launcher release check / self-update | NOT TESTED | Real GitHub release-check code; silent self-update intentionally disabled until a signed channel exists. |
| Silent NSIS installation | PASSED | Installed into a new directory on Windows; full packaged EXE SHA256 verified, including the exact standard Tauri bundle-type transformation. |
| Silent NSIS uninstallation | PASSED | Installed EXE removed; an app-data preservation probe remained unchanged. The probe is not a Minecraft world. |
| Interactive installer / first WebView2 bootstrap / SmartScreen | NOT TESTED | CI silent execution does not establish a clean standard-user interactive installation. No signing certificate supplied. |

## Resolved installer-test false failure

The first harness incorrectly expected the installed EXE to be byte-identical to the standalone Cargo output. Tauri intentionally changes `__TAURI_BUNDLE_TYPE_VAR_UNK` to `__TAURI_BUNDLE_TYPE_VAR_NSS` before NSIS packaging, then restores the standalone binary. This is documented in the [Tauri bundler implementation](https://github.com/tauri-apps/tauri/blob/8d3efdeea5b4221301a4fc5e2d4ab969abe8aa7f/crates/tauri-bundler/src/bundle.rs#L40-L96).

The corrected test reproduces **only that exact first-token transformation**, computes the complete expected SHA256 and compares the installed file with it. Other differences still fail. The NSIS archive was also independently extracted in the development environment and compared byte-for-byte with that expected payload. The retest of the exact delivered package passed installation and uninstallation. The earlier `INSTALLER-SMOKE` file in the original build artifact records the superseded assertion; the final installer report is from run **39655709172**.

## Delivered file identity

| File | Bytes | SHA256 |
|---|---:|---|
| `WinLauncher.exe` | 19,104,768 | `b32220878ef44678dcef15ca012ec38bb3cef63170e06027811da1c805252406` |
| `WinLauncher-Setup.exe` | 5,980,056 | `1dfc402f6381d9775db9a2d85ac9b10dc569e6a7984ed0d9527e2593b38e678e` |

The app is a genuine PE32+ x86-64 Windows GUI executable. The NSIS launcher stub is PE x86 and installs that x64 application. Both lack Authenticode signatures. The installed NSIS payload SHA256 is `2049bc5470a86a7f28c042fdc9f778e47bcc688668717b9ffe5c2be340db4bd3`, differing from the standalone EXE only in the standard Tauri bundle marker described above.

## Manual acceptance still required

1. Install as a standard Windows 10/11 user. Check a clean WebView2 bootstrap, language selection, Start Menu shortcut, opening/closing and high DPI.
2. Configure your approved Microsoft Application ID. Test device login, cancellation, expiry, refresh, unlicensed-account rejection and switching. Confirm secrets are absent from SQLite and logs.
3. Launch Vanilla and each loader with a licensed account. Verify JVM version, RAM arguments, native rendering, PID, stdout/stderr, normal/abnormal exit and real statistics.
4. Test historical/snapshot versions, interrupted downloads, corrupted files, cancellation and insufficient disk space.
5. Configure your own CurseForge key in the native UI. Test compatible/incompatible/pinned dependencies, restricted `downloadUrl=null` files and representative ZIP packs.
6. Configure Discord Desktop and Application ID. Verify every privacy toggle, transitions, elapsed time, disable/clear and tray behavior.
7. Use copies of real worlds to test backup/restore and migration across volumes; originals must remain recoverable.
8. Verify settings and themes through a full native application restart, every biome and language, keyboard navigation and screen readers.

## Known scope limits

- Explicit local offline identity is now supported on request. It never claims Microsoft authentication/ownership. No third-party launcher Client IDs are bundled.
- Legacy Forge installers without headless support can fail with a clear error; historical compatibility is not universally verified.
- Independent overrides for every storage subdirectory are not implemented; whole-root migration and per-profile Java/RAM are available.
- Datapacks are discoverable/openable but are not automatically written into a selected world.
- Existing modpacks are not replaced in-place. Import an updated pack into a new profile and use backups for world migration.
- Exports intentionally exclude user JARs, CurseForge files, worlds, private configurations and credentials.
- Auto-updating is not enabled without a signed update channel; checks/manual release links are separate.
- Russian is the complete primary copy. Navigation/main actions support English/Ukrainian, but some technical help and native errors remain Russian.
- No Windows code-signing certificate is bundled; SmartScreen may warn. Do not disable antivirus to run the application.
