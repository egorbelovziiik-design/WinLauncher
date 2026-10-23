# STEAL AN EGG ULTIMATE HUB

A standalone Windows desktop hub for the **STEAL AN EGG** Roblox test place: a dark
violet WPF control panel, a transparent in-game overlay toggled with **RIGHT SHIFT**,
JSON-persisted settings, a safe command interpreter and a working Luau bridge for your
own place.

> **What this is not:** no DLL injection, no game memory reads/writes, no client
> patching, no anti-cheat bypass, no code execution inside someone else's experience.
> The hub observes the Roblox *window* (geometry only) and talks to your own test place
> over a loopback JSON bridge.

---

## Download

**[StealAnEggUltimate.exe (62.9 MB, Win10/11 x64)](https://github.com/egorbelovziiik-design/WinLauncher/raw/saeu-latest/StealAnEggUltimate/dist/StealAnEggUltimate.exe)** —
the file also lives in `StealAnEggUltimate/dist/`; no .NET install needed.
All links, SHA256 and run modes: [DOWNLOAD.md](DOWNLOAD.md).

## 1. Requirements

| Item | Version |
| --- | --- |
| OS | Windows 10 1809+ / Windows 11, x64 |
| Runtime | none — the EXE is self-contained (.NET 8) |
| Roblox | Roblox Player (for the overlay) and/or Roblox Studio (for the Luau test place) |
| Build only | .NET 8 SDK, Windows |

## 2. Run

```
dist\StealAnEggUltimate.exe
```

1. Start **Roblox Studio**, open your test place with the modules installed
   (`RobloxStudio\TestPlaceModules\README.md`), press **Play**.
2. Start the hub → it prints the loopback endpoint (`http://127.0.0.1:8787`).
3. Click **CONNECT**. The overlay attaches to the Roblox client window.
4. Press **RIGHT SHIFT** in game — the menu fades/slides in. Drag its header to move it,
   close it again with RIGHT SHIFT or ✕. Position and scale are saved.

If Roblox is not running, **CONNECT** attaches the same overlay to your desktop in
**PREVIEW** mode: RIGHT SHIFT opens the menu, dragging/moving/scaling work, the demo ESP
draws eggs, every tab and the automation loops are usable right away. The moment a Roblox
client appears the overlay moves itself onto the game window and the status pill switches
from `Overlay Connected · PREVIEW` to `Overlay Connected`. Closing Roblox returns the
overlay to the desktop preview instead of leaving a dead window behind.

No Roblox? Everything still works in **demo mode**: a generated 26-egg island lets you
test ESP, filters, tracker, collections, the demo economy and all automation loops.

## 3. Feature list

### Main window
* Custom chrome, animated neon background, violet palette, 4 themes (Violet / Cyber / Ember / Mono), rainbow accent mode.
* CONNECT / DISCONNECT / EXECUTE / CLEAR / SETTINGS / EXIT — all wired to real behaviour.
* Status pills: Roblox Detected / Not Found, Overlay Connected / Disconnected, version.
* Animated connection ring, animated toggles, sliders, cards, hover states.
* Console with coloured, timestamped output and auto-scroll.

### Egg tab
Egg ESP · Rare Egg ESP · Best Egg Finder · Egg Scanner · Egg Value Display ·
Rare Egg Notifications · Egg Distance Display · Egg Rarity Filter · Egg Tracker ·
Egg List — each with an ON/OFF switch, all functional.
Plus: two configurable ESP colours (presets + custom hex), six rarity filters,
minimum value / max distance sliders, discovered-egg list with TRACK buttons,
best-egg finder, live data-source badge (DEMO / OFFICIAL / SNAPSHOT).

### Player tab
Speed · Fly · Noclip · Infinite Jump · Teleport · Jump Power · Movement Settings ·
Player Information.
Real in the official test place (Luau: `Humanoid.WalkSpeed/JumpPower`, `BodyVelocity`
flight, collision toggle, infinite jump, CFrame teleport, configurable keys).
Elsewhere the toggles are explicitly labelled **PREVIEW** and never touch the game.

### Automation tab
Auto Collect · Auto Return · Auto Farm · Auto Steal · Auto Buy · Auto Sell ·
Auto Egg Finder · Auto Rare Egg Tracker.
Interval (200–5000 ms), actions per cycle, target selection, rarity priority,
notifications, auto-start, START/STOP with live cycle/action/uptime stats.
Demo mode runs the loop on the demo island; the official place runs it server-side.

### Visuals tab
ESP colours, overlay opacity, UI scale, FPS counter, notifications, custom crosshair
(3 styles, size), theme selector, rainbow mode — all applied live and persisted.

### Settings tab
Config save / load / export / reset (`%AppData%\StealAnEggUltimate\config\settings.json`),
keybind manager (passive global hotkey; the key is never swallowed), auto-start overlay,
check updates (GitHub releases API), application information, logs, data folders,
unload overlay, reset menu position, diagnostics.

### Command editor
```
overlay.show()      overlay.hide()      overlay.toggle()
egg.scan()          egg.trackbest()     egg.notify(true|false)
esp.color("#C084FC")
player.teleport(x, y, z)
automation.start()  automation.stop()   automation.interval(1500)
settings.save()     settings.load()
hub.status()        hub.eggs()          console.clear()      help()
```
A hand-written, whitelist-only interpreter: literal arguments only, no reflection, no
`eval`, no process access. Unknown commands are rejected with a clear error.

## 4. Limitations (honest)

* The hub cannot read another developer's experience. ESP data comes from the bundled
  Luau bridge (your place), a JSON snapshot file, or the built-in demo world.
* Character control/automation is real **only** in your test place. In third-party
  experiences those toggles are previews — the UI says so on every row (`TEST PLACE` badge).
* Some Roblox Studio builds block `HttpService` requests to loopback addresses. Then the
  bridge prints `bridge unreachable` and the hub automatically falls back to demo/snapshot
  mode. Snapshot files from Studio still work (see `RobloxStudio/TestPlaceModules/README.md`).
* The overlay is a layered WPF window over the Roblox client area; exclusive fullscreen
  is not a supported Roblox mode — use windowed or borderless.

## 5. Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

or manually:

```powershell
dotnet restore
dotnet build -c Release
dotnet publish -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:EnableCompressionInSingleFile=true -o dist
```

Result: `dist\StealAnEggUltimate.exe` (~150 MB self-contained, single file).

CI: `.github/workflows/build-hub.yml` builds and publishes the EXE on every push that
touches `StealAnEggUltimate/**` and uploads it as the `StealAnEggUltimate-win-x64` artifact.

## 6. Project layout

```
StealAnEggUltimate/
├── App/            App.xaml(.cs), MainWindow.xaml(.cs), MainViewModel.cs
├── Overlay/        OverlayWindow.xaml(.cs), OverlayManager.cs, HotkeyManager.cs, OverlayViewModel.cs
├── Features/       EggFeatures.cs, PlayerFeatures.cs, AutomationFeatures.cs, VisualFeatures.cs
├── Services/       RobloxDetector.cs, SettingsManager.cs, CommandManager.cs, Logger.cs,
│                   BridgeServer.cs, SnapshotService.cs, FeatureRegistry.cs, Win32.cs, AppInfo.cs, AppPaths.cs
├── Models/         AppSettings.cs, Egg.cs, FeatureRowViewModel.cs, RelayCommand.cs, ObservableObject.cs
├── Converters/     Converters.cs
├── Themes/         Colors.xaml, Controls.xaml
├── RobloxStudio/TestPlaceModules/  EggWorld.lua, SAEUltimateBridge.server.lua,
│                                   SAEUltimateClient.client.lua, README.md
├── Config/         sample-snapshot.json
├── Docs/           RUN.md, TESTING.md, ARCHITECTURE.md
├── build.ps1
└── StealAnEggUltimate.csproj
```

## 7. Data & logs

| Path | Contents |
| --- | --- |
| `%AppData%\StealAnEggUltimate\config\settings.json` | all settings (auto-saved, atomic write) |
| `%AppData%\StealAnEggUltimate\logs\hub-YYYYMMDD.log` | rotating log (10 files, 4 MB max) |
| `%AppData%\StealAnEggUltimate\snapshots\` | read-only JSON snapshots (watched live) |
| `%AppData%\StealAnEggUltimate\bridge\` | mirrored bridge state / last snapshot for debugging |

## 8. Headless self-test and smoke run

### Mouse behaviour of the overlay

The overlay is click-through everywhere except the open menu: the mouse position is
polled 25×/s and only while the cursor is over the menu does the window accept input
(so the command box can be typed into). Everything else — aiming, shooting, clicking in
the game — goes straight to Roblox; RIGHT SHIFT hands the keyboard back to the game.

### Screenshots of the running app

`Docs/ui/hub-window.png` and `Docs/ui/overlay-menu.png` are rendered by the app itself
during the `--smoke` run (CI regenerates them on every push), so the repository always
shows the current build instead of a mock-up.

The published EXE can verify itself without a Roblox session — the same commands the
CI workflow (`.github/workflows/build-hub.yml`) runs on `windows-latest`:

```powershell
# 39 functional assertions (settings, commands, demo egg world, ESP, bridge, assets, ...)
.\dist\StealAnEggUltimate.exe --selftest
Get-Content "$env:APPDATA\StealAnEggUltimate\selftest-report.txt"

# starts the real WPF window + renders the overlay, then auto-closes (~5 s)
.\dist\StealAnEggUltimate.exe --smoke
Get-Content "$env:APPDATA\StealAnEggUltimate\smoke-result.txt"
```

| Marker | Meaning |
| --- | --- |
| `self-test exit code 0` | all assertions passed |
| `RESULT: PASS` / `SUMMARY: n passed, m failed, k skipped` | machine-readable verdict in `selftest-report.txt` |
| `[SKIP]` line | the skipped item is "overlay attached to a real Roblox window": it needs the Roblox Player running. The desktop PREVIEW overlay, its menu and its render are covered by `--smoke` |
| `smoke ok ... uiExceptions=0` | CONNECT attached the overlay, the menu opened, the demo ESP projected eggs, both windows rendered to PNG with real pixels |
| `Docs/ui/hub-window.png`, `Docs/ui/overlay-menu.png` | screenshots rendered by the app itself during the smoke run |
| smoke exit code `4` | the UI rendered but raised at least one binding/XAML exception (CI fails) |

Everything that could not be executed automatically is marked here instead of being
reported as a success. Covered automatically: config persistence, command interpreter,
demo egg world, ESP projection/filters/filters, snapshots, loopback bridge round-trip,
demo automation, bundled Luau assets, Roblox process/window enumeration, the passive
keyboard hook, the preview overlay geometry, and a full render of the hub window plus the
in-game menu. NOT covered: the Luau modules running inside Roblox Studio (`TEST PLACE`
path) and the overlay following a live game window — both need a real client and are
documented step by step in `Docs\RUN.md`.
