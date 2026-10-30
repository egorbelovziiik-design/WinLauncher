# AXSESCLIENT — GENESIS X (v1.0.0)

**Next-Generation Minecraft Utility, HUD, Visual & Fair-Play PvP Training Client + Native Windows Desktop Launcher**

- **Product:** `AXSESCLIENT`
- **Codename:** `GENESIS X`
- **Version:** `1.0.0`
- **Target Operating System:** Windows 10 / Windows 11 (x64)
- **Supported Minecraft Versions:** `1.16.5` (Java 8 • Fabric `0.20.1`), `1.21.4` (Java 21 • Fabric `0.20.1`), `1.21.11` (Java 21 • Fabric `0.20.1`)
- **Design System:** `AXSES NEO-DARK` (`#070911` • `#10131D` • `#191D2B` • `#9060FF` • `#536FFF` • `#00D9FF`)

---

## 1. Release Deliverables (`AXSESCLIENT-RELEASE/`)

| Deliverable | Description |
|---|---|
| `AXSESCLIENT-Setup.exe` | Official Windows 10/11 x64 installer (Inno Setup 6). Installs `AXSESCLIENT.exe`, all 3 version-specific Fabric client JARs, themes, schemas, and optional Desktop/Start Menu shortcuts while preserving existing user worlds and settings. |
| `AXSESCLIENT.exe` | Self-contained C# / .NET 8 / Avalonia UI 11 Windows x64 Desktop Launcher with direct 13-stage JVM launch engine, Version Manager, Microsoft OAuth2 + PKCE vault, Auto-Updater, and 90-module pre-configurator. |
| `AXSESCLIENT-1.16.5.jar` | Native Fabric client mod for **Minecraft 1.16.5** (Java 8 bytecode `v52`, `PoseStack` + `GuiComponent` renderer). |
| `AXSESCLIENT-1.21.4.jar` | Native Fabric client mod for **Minecraft 1.21.4** (Java 21 bytecode `v65`, `GuiGraphics` renderer + `OptionInstance` gamma pipeline). |
| `AXSESCLIENT-1.21.11.jar` | Native Fabric client mod for **Minecraft 1.21.11** (Java 21 bytecode `v65`, JOML `Matrix3x2fStack` 2D pose stack + `MouseButtonEvent`/`KeyEvent` input records + `Identifier` APIs). |
| `README.md` | Architecture, installation, usage, and build guide. |
| `CHANGELOG.md` | Release notes for `v1.0.0` (`GENESIS X`). |
| `BUILD_REPORT.md` | Verified CI build, unit test, Xvfb real-client test, and Windows installer verification report + honest known limitations. |
| `DESIGN_RESEARCH.md` | Comparative UX, HUD, ClickGUI, and architectural analysis of 50 Minecraft clients & launchers across 14 criteria. |
| `LICENSES.md` | Project license (MIT) and third-party open-source attribution & Mojang compliance disclosures. |

---

## 2. Quick Start (Windows 10 / 11 x64)

### Option A — Recommended Installer (`AXSESCLIENT-Setup.exe`)
1. Run `AXSESCLIENT-Setup.exe`.
2. Choose your installation directory (defaults to `%LOCALAPPDATA%\Programs\AXSESCLIENT` — no UAC administrator elevation required) and optional Desktop shortcut.
3. Launch **AXSESCLIENT**.
4. Open **Accounts** -> click **Sign in with Microsoft (Browser OAuth2)** to sign in via your system browser and verify Minecraft: Java Edition ownership (or create a **Local Singleplayer Test Profile** for offline singleplayer testing).
5. Open **Versions** or **Dashboard**, select `1.16.5`, `1.21.4`, or `1.21.11`, and click **PLAY** (automatically downloads & SHA-1-verifies official Mojang + Fabric libraries, provisions isolated Eclipse Temurin Java 8 / Java 21 if needed, deploys the matching `AXSESCLIENT-{version}.jar`, and launches Minecraft directly).

### Option B — Manual Fabric Mod Installation
If you prefer using an existing Fabric installation:
- Copy **only** the matching JAR into that version's `mods/` folder:
  - Minecraft `1.16.5` (Fabric Loader `>= 0.16.0`, Java 8+) -> `AXSESCLIENT-1.16.5.jar`
  - Minecraft `1.21.4` (Fabric Loader `>= 0.16.0`, Java 21+) -> `AXSESCLIENT-1.21.4.jar`
  - Minecraft `1.21.11` (Fabric Loader `>= 0.18.0`, Java 21+) -> `AXSESCLIENT-1.21.11.jar`
- Each `AXSESCLIENT-{version}.jar` embeds core Fabric lifecycle/keybinding/rendering API modules via Jar-in-Jar so it runs out of the box.

---

## 3. In-Game Controls & ClickGUI

- **Open ClickGUI:** Press **`RIGHT SHIFT`** (works both in-world and from the `AXSESCLIENT MAIN MENU`; rebindable in `Settings -> Keyboard Shortcut Editor`).
- **Close ClickGUI:** Press **`RIGHT SHIFT`** or **`ESCAPE`**.
- **Open HUD Editor:** Click **`HUD EDITOR`** in the top bar of ClickGUI (or bind a dedicated key).
  - **Drag & Drop:** Left-click and drag any enabled HUD widget; magnetic centerlines and 8px grid snapping assist alignment.
  - **Scale Widget:** Hover a widget and scroll the mouse wheel (`0.50x – 2.50x`), or click `+` / `-` in the floating inspector.
  - **Toggle Shadow / Outline / Lock / Group:** Use the floating HUD inspector bar or apply instant presets (`Competitive`, `Minimal`, `Survival`, `Streamer`).
- **Smooth Zoom:** Hold **`C`** (default, rebindable) and scroll the mouse wheel to adjust magnification smoothly.
- **FreeLook:** Hold **`V`** (default, rebindable) for 360° camera orbit while preserving movement direction.

---

## 4. Repository Architecture (`AXSESCLIENT/`)

```text
AXSESCLIENT/
├── launcher/        # C# .NET 8 + Avalonia UI 11 Windows Desktop Launcher (AXSESCLIENT.exe)
├── updater/         # Transactional SHA-256 Auto-Update & Rollback library (AxsesClient.Updater)
├── client-common/   # Shared Java 8-compatible AXSES CORE engine (13 managers + 90 modules + JUnit 5)
├── client-1.16.5/   # Fabric 1.16.5 adapter, screens & Mixins -> AXSESCLIENT-1.16.5.jar
├── client-1.21.4/   # Fabric 1.21.4 adapter, screens & Mixins -> AXSESCLIENT-1.21.4.jar
├── client-1.21.11/  # Fabric 1.21.11 adapter, JOML renderer, input records & Mixins -> AXSESCLIENT-1.21.11.jar
├── shared/          # AXSES NEO-DARK design tokens, 8 official theme JSONs, JSON schemas, update manifest
├── assets/          # Procedural vector/raster branding generator, .ico, PNGs, and category SVG icons
├── installer/       # Inno Setup 6 Windows x64 installer script -> AXSESCLIENT-Setup.exe
├── tests/           # xUnit + Avalonia.Headless Skia UI & launch-engine test suite
└── documentation/   # DESIGN_RESEARCH.md, README.md, CHANGELOG.md, BUILD_REPORT.md, LICENSES.md
```

---

## 5. Fair-Play, Security & Privacy Guarantees

1. **Strict Fair-Play:** AXSESCLIENT contains **zero** anti-cheat bypasses, client brand spoofing, reach modification, aim-assist, auto-clickers/auto-attack, packet manipulation, or hidden entity/cave revealers. Interactive combat training drills (`Training Target`, `Damage Feedback`, `Attack Timing Practice`, `Reaction Training`) are strictly gated to Singleplayer (`CompatibilityEngine#isTrainingAllowed`).
2. **Account Security:** Microsoft sign-in uses official browser OAuth2 + PKCE (`S256`). AXSESCLIENT never asks for your Microsoft password in a custom window, encrypts refresh/access tokens at rest using **Windows DPAPI (`ProtectedData.Protect`, `CurrentUser`)**, and scrubs tokens from all console/log output.
3. **Zero Proprietary Redistribution:** Neither the repository nor `AXSESCLIENT-Setup.exe` bundles proprietary Mojang `client.jar` or game assets; official files are downloaded directly from `piston-meta.mojang.com` and `resources.download.minecraft.net` after installation.
4. **World & Config Safety:** Upgrades and uninstallation never delete user `saves/`, `screenshots/`, `resourcepacks/`, or `axsesclient/configs/`. Corrupt JSON config or theme files automatically back up to `.corrupt.<timestamp>.bak` and recover cleanly to defaults without crashing.

---

## 6. Building From Source

### Prerequisites
- **Java:** Eclipse Temurin JDK 8 (for `1.16.5` runtime verification) + JDK 21 (for Gradle 8.12 / Loom 1.19.3 and `1.21.4` / `1.21.11`)
- **.NET:** .NET 8.0 SDK (`8.0.x`)
- **Installer (Windows):** Inno Setup 6 (`ISCC.exe`)

### Commands
```bash
# 1. Run AXSES CORE unit tests
cd AXSESCLIENT/client-common && gradle test build

# 2. Build per-version Fabric client JARs
cd ../client-1.16.5  && gradle remapJar
cd ../client-1.21.4  && gradle remapJar
cd ../client-1.21.11 && gradle remapJar

# 3. Run .NET xUnit + Avalonia Headless UI tests
cd ../..
dotnet test AXSESCLIENT/AXSESCLIENT.sln -c Release

# 4. Publish Windows x64 Launcher executable
dotnet publish AXSESCLIENT/launcher/AxsesClient.Launcher.csproj \
  -c Release -r win-x64 --self-contained true \
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true \
  -p:EnableCompressionInSingleFile=true -o AXSESCLIENT-RELEASE

# 5. Compile Windows x64 Installer (on Windows with Inno Setup 6)
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" AXSESCLIENT\installer\AXSESCLIENT-Setup.iss
```
