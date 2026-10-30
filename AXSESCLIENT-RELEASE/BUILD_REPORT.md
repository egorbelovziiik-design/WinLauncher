# AXSESCLIENT (Codename: GENESIS X) v1.0.0 — Engineering Build & Verification Report

- **Release Version:** `1.0.0` (`GENESIS X`)
- **Build Date:** `2026-10-31`
- **Repository & Branch:** `egorbelovziiik-design/WinLauncher` (`arena/b5b3cc60-winlauncher`)
- **Verified CI Pipeline Run:** GitHub Actions Run `41138065290`
  - Job `124345321985` — *Build Fabric Mods & Real C#-Engine Xvfb Minecraft Tests (1.16.5, 1.21.4, 1.21.11)*: **PASSED**
  - Job `124347071509` — *Package & Verify Windows x64 AXSESCLIENT.exe & AXSESCLIENT-Setup.exe*: **PASSED**

---

## 1. Deliverable Artifacts & SHA-256 Checksums

All compiled deliverables and documentation files are staged in `/home/user/WinLauncher/AXSESCLIENT-RELEASE/` (and mirrored in `/home/user/WinLauncher/AXSESCLIENT/release/` metadata):

| Artifact | Path | Byte Size | SHA-256 Checksum | Target Runtime |
|---|---|---:|---|---|
| **Windows x64 Launcher** | `AXSESCLIENT-RELEASE/AXSESCLIENT.exe` | `45,560,586` | `3d4a5c4036b270b174b0af0f098f90eb884536ce5948c95847bc4053fe743292` | Self-contained Windows 10/11 x64 (.NET 8 + Avalonia 11.2.5) |
| **Windows x64 Installer** | `AXSESCLIENT-RELEASE/AXSESCLIENT-Setup.exe` | `41,551,379` | `366f8b058b98d9f506a2deb8edb1610d9f750e5dcb5b4ff57607eb6b54b8603a` | Windows 10/11 x64 (Inno Setup 6, LZMA2/max, non-admin per-user default) |
| **Minecraft 1.16.5 Mod** | `AXSESCLIENT-RELEASE/AXSESCLIENT-1.16.5.jar` | `348,634` | `c7828ab63f69005477901d1e5727191e77a1a5f08154f59350a64affa19990cd` | Java 8 (`class major 52`) + Fabric Loader `0.20.1+` |
| **Minecraft 1.21.4 Mod** | `AXSESCLIENT-RELEASE/AXSESCLIENT-1.21.4.jar` | `354,812` | `0dddd8ce61b10d55e7c18accb7cfe52cdad20a64f8bfc5382d7211118187bbcc` | Java 21 (`class major 65`) + Fabric Loader `0.20.1+` |
| **Minecraft 1.21.11 Mod** | `AXSESCLIENT-RELEASE/AXSESCLIENT-1.21.11.jar` | `356,133` | `be945e263988633270d45a86558d3758b0e0e95a73597d1e589917c97aab121b` | Java 21 (`class major 65`) + Fabric Loader `0.20.1+` |

Documentation bundle in `AXSESCLIENT-RELEASE/`:
- `AXSESCLIENT-RELEASE/README.md`
- `AXSESCLIENT-RELEASE/CHANGELOG.md`
- `AXSESCLIENT-RELEASE/BUILD_REPORT.md`
- `AXSESCLIENT-RELEASE/DESIGN_RESEARCH.md`
- `AXSESCLIENT-RELEASE/LICENSES.md`
- `AXSESCLIENT-RELEASE/SHA256SUMS.txt`

---

## 2. Build Toolchain Matrix

| Component | Toolchain / SDK | Configuration |
|---|---|---|
| `client-common` (`AXSES CORE`) | Gradle `9.8.2`, `options.release = 8`, JUnit Jupiter `5.10.2`, Gson `2.10.1` | Shared pure-Java core compiled to Java 8 (`v52`) bytecode so it links cleanly into both Java 8 (`1.16.5`) and Java 21 (`1.21.4` / `1.21.11`) targets |
| `client-1.16.5` | Fabric Loom `1.19.3` (`fabric-loom-remap`), Official Mojang 1.16.5 mappings, Fabric Loader `0.20.1`, `options.release = 8` | Standalone Fabric mod (`AXSESCLIENT-1.16.5.jar`); verified `javap -verbose` classfile version `52.0` across both adapter and embedded `client-common` classes |
| `client-1.21.4` | Fabric Loom `1.19.3` (`fabric-loom-remap`), Official Mojang 1.21.4 mappings, Fabric Loader `0.20.1`, `options.release = 21` | Standalone Fabric mod (`AXSESCLIENT-1.21.4.jar`); verified classfile version `65.0` |
| `client-1.21.11` | Fabric Loom `1.19.3` (`fabric-loom-remap`), Official Mojang 1.21.11 mappings, Fabric Loader `0.20.1`, `options.release = 21` | Standalone Fabric mod (`AXSESCLIENT-1.21.11.jar`) adapted to 1.21.11 `GuiGraphics`, `RenderPipelines`, and `DebugScreenEntries`; verified classfile version `65.0` |
| `launcher` & `updater` | .NET `8.0` SDK, C# 12, Avalonia UI `11.2.5`, `System.Security.Cryptography.ProtectedData 8.0.0` | Published `-c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true` |
| `installer` | Inno Setup `6` (`ISCC.exe`) | `ArchitecturesAllowed=x64compatible`, `PrivilegesRequired=lowest`, `Compression=lzma2/max`; bundles zero proprietary Mojang files |

---

## 3. End-to-End Verification Summary

### 3.1 `client-common` JUnit 5 Suite (`gradle test`)
- **Status:** **PASSED**
- **Verified subsystems:**
  - Exact registration of all **90 modules & GUI features** across all **8 ClickGUI categories** (`Combat Training`, `Movement`, `Render`, `Player`, `World`, `Utility`, `HUD`, `Settings`) with non-empty settings and real runtime behavior.
  - All 7 setting primitives (`BooleanSetting`, `NumberSetting`, `ModeSetting`, `ColorSetting`, `KeybindSetting`, `StringSetting`, `MultiSelectSetting`), step quantization, min/max clamping, hex/ARGB parsing, and keybind conflict detection.
  - `ThemeManager` loading all **8 AXSES NEO-DARK themes** plus custom theme creation, JSON export/import, and schema validation.
  - `ConfigManager` profile creation, save, load, rename, delete, import, export, backup, restore, and non-crashing recovery from deliberately corrupted JSON files.
  - `ClickGuiController` (`RIGHT SHIFT` `344` open/close, `ESCAPE` `256` close, rebindable keybind, search history, favorites, category filter) and `HudEditorController` (drag-and-drop, snapping, centering, grouping, lock, multi-resolution preview, layout presets).
  - Fair-play singleplayer/authorized-server gating on gameplay-modifying `Combat Training` modules (`Training Target`, `Reaction Training`).

### 3.2 Real End-to-End Minecraft Launches via C# `MinecraftLaunchEngine`
Each Minecraft target was downloaded from official Mojang Piston Meta + Fabric Meta APIs by our C# `MinecraftLaunchEngine`, provisioned with isolated `instances/<version>/`, launched directly via JVM process invocation under `xvfb-run` + Mesa OpenGL 4.5 (`llvmpipe`), audited via `MixinEnvironment.getCurrentEnvironment().audit()`, ticked into the client engine with `AXSESCLIENT-<version>.jar` loaded, and verified via JSON self-test telemetry:

- **Minecraft `1.16.5` (`AXSESCLIENT-1.16.5.jar` on Eclipse Temurin Java `1.8.0_504`):**
  ```json
  {
    "status": "PASS",
    "minecraftVersion": "1.16.5",
    "javaRuntime": "1.8.0_504",
    "modulesRegistered": 90,
    "themesRegistered": 8,
    "activeTheme": "axses-purple",
    "effectiveGamma": 12.0,
    "mixinAudit": "PASS"
  }
  ```
- **Minecraft `1.21.4` (`AXSESCLIENT-1.21.4.jar` on Eclipse Temurin Java `21.0.13`):**
  ```json
  {
    "status": "PASS",
    "minecraftVersion": "1.21.4",
    "javaRuntime": "21.0.13",
    "modulesRegistered": 90,
    "themesRegistered": 8,
    "activeTheme": "axses-cyber",
    "effectiveGamma": 12.0,
    "mixinAudit": "PASS"
  }
  ```
- **Minecraft `1.21.11` (`AXSESCLIENT-1.21.11.jar` on Eclipse Temurin Java `21.0.13`):**
  ```json
  {
    "status": "PASS",
    "minecraftVersion": "1.21.11",
    "javaRuntime": "21.0.13",
    "modulesRegistered": 90,
    "themesRegistered": 8,
    "activeTheme": "axses-neon",
    "effectiveGamma": 12.0,
    "mixinAudit": "PASS"
  }
  ```

### 3.3 Windows x64 `AXSESCLIENT.exe`, Headless Skia UI Suite, & `AXSESCLIENT-Setup.exe` Verification
Executed natively on `Microsoft Windows 10.0.26100` (`windows-latest` x64):
1. **xUnit + Avalonia Headless Skia Suite (`dotnet test -c Release`):** **PASSED**, rendering and verifying all 11 launcher pages (`Dashboard`, `Play`, `Versions`, `Accounts`, `Profiles`, `Modules`, `Appearance`, `Settings`, `Downloads`, `Diagnostics`, `About`) into `AXSESCLIENT-RELEASE/screenshots/launcher-*.png`.
2. **Compiled `AXSESCLIENT.exe --selftest`:** **PASSED** (`dpapiPrefix: "dpapi"`, `tokenScrubVerified: true`, `modulesCount: 90`, `themesCount: 8`, live HTTPS reachability to Mojang Piston Meta `HTTP 200` and Fabric Meta API `HTTP 200`, and SHA/size verification of all 3 bundled JARs).
3. **Silent Install / Launch / World-Safe Uninstall (`AXSESCLIENT-Setup.exe`):**
   - Pre-seeded `%APPDATA%\AXSESCLIENT\instances\1.21.4\saves\ProtectedUserWorld\level.dat`.
   - Executed `AXSESCLIENT-Setup.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART`, verified installed `AXSESCLIENT.exe` and `mods\AXSESCLIENT-{1.16.5,1.21.4,1.21.11}.jar`, and ran installed `AXSESCLIENT.exe --selftest` (**PASS**).
   - Executed `unins000.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART`, waited until `AXSESCLIENT.exe` was completely removed, and verified `ProtectedUserWorld\level.dat` remained **100% intact**.

---

## 4. Known Limitations & Honest Engineering Notes

1. **Windows SmartScreen / Code Signing:** `AXSESCLIENT.exe` and `AXSESCLIENT-Setup.exe` are built reproducibly without an Authenticode EV certificate. On first run on retail Windows 10/11 systems with SmartScreen enabled, Windows may display an "Unknown publisher" prompt until signed with an organization Authenticode certificate.
2. **Microsoft OAuth Client ID Provisioning:** `MicrosoftAuthService` implements the official system-browser Authorization Code + PKCE flow (`login.microsoftonline.com/consumers/oauth2/v2.0/authorize` -> Xbox Live `user.auth.xboxlive.com` -> XSTS `xsts.auth.xboxlive.com` -> `api.minecraftservices.com/authentication/login_with_xbox` -> `entitlements/mcstore` Java Edition ownership verification -> `minecraft/profile`) and never asks for passwords inside the app. Production deployments distributing to external users must supply their own approved Microsoft Entra / Xbox Live Title Client ID via the `AXSES_MSA_CLIENT_ID` environment variable or Launcher Settings; for immediate local testing and offline singleplayer verification, Local Sandbox Mode is built into the Accounts page.
3. **Zero `fabric-api` Requirement & Third-Party Mod Compatibility:** All three `AXSESCLIENT-*.jar` mods depend **only** on `fabricloader` (`>=0.16.0`, provisioned at `0.20.1`) and standard Minecraft classes, so users do not need to install `fabric-api` for AXSESCLIENT to run. Users who voluntarily drop third-party Fabric mods (such as Sodium, Iris, or ModMenu) into an instance's `mods/` folder may need to add the matching version of `fabric-api` if those third-party mods require it; `Module Compatibility Viewer` surfaces loaded mod conflict guidance in-game.
4. **HUD Background Blur Performance Mode:** To avoid brittle core-shader patching across 1.16.5 OpenGL 2.1, 1.21.4 OpenGL 3.2, and 1.21.11 `RenderPipelines`, `HUD Background Blur` uses multi-pass translucent frosted-glass layering with dynamic alpha falloff rather than custom post-processing framebuffer shaders, ensuring zero crashes with Sodium/Iris or software Mesa drivers at the cost of a subtler blur kernel than multi-pass Gaussian FBO shaders.
5. **Fair-Play Singleplayer Gate on Gameplay Training Modules:** By design, `Training Target` and `Reaction Training` spawn client-side visual practice markers only in singleplayer worlds (or servers explicitly advertising AXSES training mode) and automatically suspend on public multiplayer servers.
