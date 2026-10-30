# Changelog — AXSESCLIENT

All notable changes to **AXSESCLIENT** are documented in this file.

## [1.0.0] — 2026-10-30 (Codename: `GENESIS X`)

### Added — Windows Desktop Launcher (`AXSESCLIENT.exe`)
- Native **C# / .NET 8 / Avalonia UI 11** Windows x64 desktop application styled with the **AXSES NEO-DARK** design system.
- All **11 functional pages**: `Dashboard`, `Play`, `Versions`, `Accounts`, `Profiles`, `Modules`, `Appearance`, `Settings`, `Downloads`, `Diagnostics`, and `About`.
- **13-Stage Direct Minecraft Launch Engine** (`MinecraftLaunchEngine.cs`):
  - Automatic Java 8 (`1.16.5`) and Java 21 (`1.21.4` & `1.21.11`) runtime detection + Eclipse Temurin auto-provisioning.
  - Official Mojang `version_manifest_v2.json` + Fabric Meta `v2/versions/loader/{mc}/0.20.1/profile/json` resolution with Fabric Maven library precedence.
  - Parallel HTTP downloads with exponential retry and strict SHA-1/SHA-256 verification.
  - Native library extraction (`1.16.5` classifiers and `1.21.x` embedded natives) and direct `KnotClient` JVM process spawning with token-redacted live console streaming.
- **Version Manager** supporting states `NOT INSTALLED`, `INSTALLING`, `INSTALLED`, `UPDATE AVAILABLE`, `REPAIR REQUIRED`, `LAUNCHING`, `RUNNING` and actions `INSTALL`, `PLAY`, `UPDATE`, `REPAIR`, `OPEN FOLDER`.
- **Official Browser-Based Microsoft OAuth2 + PKCE Vault** (`MicrosoftAuthService.cs` + `SecureTokenVault.cs`):
  - Loopback callback listener (`127.0.0.1`), Xbox Live -> XSTS -> Minecraft Services authentication, Java Edition ownership check, token refresh, multi-account switcher, and Windows DPAPI (`ProtectedData.Protect`) storage.

### Added — Shared Core & Per-Version Fabric Mods (`1.16.5`, `1.21.4`, `1.21.11`)
- **`AXSES CORE` (`client-common`)**: `ModuleManager`, `EventManager`, `SettingManager`, `ConfigManager`, `ThemeManager`, `HudManager`, `KeybindManager`, `NotificationManager`, `AnimationManager`, `RenderManager`, `FontManager`, `LocalizationManager` (`en_US` & `ru_RU`), `CompatibilityEngine`, and `PerformanceProfiler`.
- **90 Real Modules & GUI Features**:
  - 30 Visual & HUD modules (`FullBright`, `Smooth Zoom`, `Custom Crosshair`, `FreeLook`, `FPS Counter`, `Coordinates HUD`, `Armor HUD`, `Potion HUD`, `Keystrokes`, `CPS Counter`, `Compass HUD`, etc.).
  - 12 Movement & Player modules (`Toggle Sprint`, `Toggle Sneak`, `Movement Speed HUD`, `Camera Bobbing`, `FOV Transitions`, `Jump Statistics`, etc.).
  - 12 Fair-Play Combat Training modules (`Attack Cooldown Indicator`, `Custom Hitmarker`, `Combat Statistics`, and singleplayer-gated `Training Target`, `Attack Timing Practice`, `Reaction Training`, etc.).
  - 20 World & Utility modules (`Waypoints`, `Death Location`, `Light Level Display`, `Chunk Border Toggle`, `Coordinate Converter`, `Durability Warning`, `Coordinate Clipboard`, etc.).
  - 16 GUI & Accessibility features (`GUI Scale Manager`, `Theme Preview`, `Keyboard Shortcut Editor`, `HUD Presets`, `Color Blindness Presets`, `Reduced Motion Mode`, `Config Backup Manager`, `Module Compatibility Viewer`, etc.).
- **AXSES PREMIUM CLICKGUI**: Opens on `RIGHT SHIFT`, closes on `RIGHT SHIFT` or `ESCAPE`, rebindable, with live search + history, favorites, 7 typed setting controls, RGB/HEX/Alpha color picker, conflict-detecting keybind selector, and layout sorting.
- **HUD Editor**: Drag & drop, scroll-wheel scaling (`0.50x–2.50x`), grid/center snapping, grouping, lock toggle, multi-resolution aspect-ratio preview (`720p`, `1080p`, `1440p`, `Ultrawide`), and 4 curated presets.
- **Theme Engine**: 8 official themes (`AXSES DARK`, `AXSES PURPLE`, `AXSES BLUE`, `AXSES CYBER`, `AXSES MIDNIGHT`, `AXSES MINIMAL`, `AXSES GRAPHITE`, `AXSES NEON`) with custom theme import/export and validation.
- **Custom Main Menu (`AXSESCLIENT MAIN MENU`)**: `SINGLEPLAYER`, `MULTIPLAYER`, `SETTINGS`, `AXSES GUI`, `ACCOUNTS`, and `QUIT` across `1.16.5`, `1.21.4`, and `1.21.11`.

### Added — Auto-Updater & Windows Installer
- **`AxsesClient.Updater`**: SHA-256 manifest verification, atomic staging, transactional apply, and versioned rollback.
- **`AXSESCLIENT-Setup.exe`**: Inno Setup 6 Windows 10/11 x64 installer with non-destructive upgrade and world-preserving uninstaller.
