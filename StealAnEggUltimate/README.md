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

If Roblox is not running yet, **CONNECT** switches to *WAITING FOR ROBLOX* and attaches
automatically as soon as the client appears. Closing Roblox auto-disconnects the overlay.

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

The published EXE can verify itself without a Roblox session — the same commands the
CI workflow (`.github/workflows/build-hub.yml`) runs on `windows-latest`:

```powershell
# 36 functional assertions (settings, commands, demo egg world, ESP, bridge, assets, ...)
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
| `[SKIP]` line | the skipped item is the overlay hotkey/menu test, which needs a real Roblox client and an interactive desktop session |
| `smoke ok ... uiExceptions=0` | main window and overlay rendered with zero XAML/binding errors |
| smoke exit code `4` | the UI rendered but raised at least one binding/XAML exception (CI fails) |

Everything that could not be executed automatically is marked here instead of being
reported as a success: with no Roblox client on the machine, only the demo island,
the snapshot reader and the loopback bridge are exercised; the in-game path
(`TEST PLACE` modules in `RobloxStudio\TestPlaceModules\`) has to be run by hand in
Roblox Studio and is documented in `Docs\RUN.md`.
