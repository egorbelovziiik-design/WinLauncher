# NEXUS CLIENT OMEGA X

**THE ULTIMATE MINECRAFT EXPERIENCE**

Nexus Client Omega X is a Minecraft: Java Edition client built from scratch as a
Fabric mod. One shared core (`NexusCore`) powers 274 modules, a full ClickGUI, a
draggable HUD, 30 chat commands, 7 themes, macros, waypoints, friends lists and
JSON config profiles — on three Minecraft versions at once, with identical
behaviour.

| | |
|---|---|
| Client version | 1.0.0 |
| Modules | 274 |
| Commands | 28 (`.help`, `.bind`, `.toggle`, `.cfg`, …) |
| HUD widgets | 26 |
| Themes | 7 (incl. CUSTOM RGB) |
| Minecraft versions | 1.16.5 (protocol 754, Java 8), 1.21.4 (769, Java 21), 1.21.11 (774, Java 21) |
| Loader | Fabric (official installer only) |
| Launcher | `NexusInjector.exe` (native Windows, 249 KB) |

---

## 1. What is in the box

```
NexusOmegaX/
├── api/                     nexus.api  - version independent interfaces
├── core/                    nexus.core - engine (events, settings, config, gui, hud, commands…)
├── modules/                 274 modules in 10 categories
├── adapters/
│   ├── nexus1165/           Minecraft 1.16.5 adapter (yarn, Java 8)
│   ├── nexus1214/           Minecraft 1.21.4 adapter (mojmap, Java 21)
│   └── nexus12111/          Minecraft 1.21.11 adapter (mojmap, Java 21)
├── launcher/                NexusInjector sources (C / Win32) + build.sh
├── test/                    headless test harness (nexus.test.TestSuite)
├── dist/
│   └── NexusInjector.exe    built Windows launcher
└── NexusOmegaX-Source.zip   everything above, ready to archive
```

Built artefacts (produced by `./build-all.sh`, see `BUILD_INSTRUCTIONS.md`):

* `NexusOmegaX-1.16.5.jar`
* `NexusOmegaX-1.21.4.jar`
* `NexusOmegaX-1.21.11.jar`
* `NexusInjector.exe`

## 2. Architecture

```
      modules (274) ──┐
      core (engine) ──┼──> nexus.api (IClient, IPlayer, IRenderer, IWorld, …)
      commands/hud  ──┘            │
                                   │ implemented by
                    ┌──────────────┼──────────────┐
             Nexus1165       Nexus1214       Nexus12111
            (MC 1.16.5)     (MC 1.21.4)     (MC 1.21.11)
```

Nothing outside the adapter touches Minecraft code. A module only ever talks to
`IClient`, `IWorld`, `IRenderer`, `IInventory` … which means:

* one bug fix in `core/` fixes every version,
* a new version costs one thin adapter (~25 files), not a port,
* the whole core can be compiled and tested **without Minecraft present** —
  that is exactly what `test/nexus/test/TestSuite` does (71 checks, headless).

### Event flow

| Vanilla hook (mixin) | Nexus method | Consumers |
|---|---|---|
| `Minecraft.runTick` / `MinecraftClient.runTick` | `Nexus.tick()` | every module `onTick` |
| `Gui.render` / `InGameHud.render` | `Nexus.render2D(event)` | GUI, HUD, 2D modules |
| `LevelRenderer.renderLevel` / `WorldRenderer.render` | `Nexus.render3D(event)` | ESP, tracers, block overlays |
| `KeyboardHandler.keyPress` / `Keyboard.onKey` | `Nexus.onKey(...)` | keybinds, ClickGUI, modules |
| `MouseHandler.onPress/onScroll` / `Mouse.*` | `Nexus.onMouse(...)` | GUI, camera helpers |
| `ChatScreen` message | `Nexus.eventBus().post(ChatSendEvent)` | commands (`.` prefix) |

## 3. Install and run (3 minutes)

> **Готовые jar-файлы (собраны в CI, ничего устанавливать не нужно):**
> `https://github.com/egorbelovziiik-design/WinLauncher/releases/download/nexus-latest/NexusOmegaX-mods.zip`
> — архив с `NexusOmegaX-1.16.5.jar`, `NexusOmegaX-1.21.4.jar`, `NexusOmegaX-1.21.11.jar`.

### Windows (one click)

```
build.bat /install      :: builds the 3 jars and puts them into .minecraft\mods
```

### Windows (with the launcher)

`NexusInjector.exe` → *INSTALL* → pick the `.minecraft` folder and the version:

1. **INSTALL FABRIC** – opens the *official* Fabric installer; press Install, close it.
2. **INSTALL CLIENT** – copies `NexusOmegaX-<version>.jar` into `.minecraft\mods\`.
3. **CREATE PROFILE** – writes a normal `Nexus Client Omega X` entry into
   `launcher_profiles.json`.
4. **LAUNCH MINECRAFT** – pick the profile in the official launcher, press Play.

`VERIFY INSTALL` checks every step and tells you what is still missing.
`install-mods.bat` copies already built jars into `mods\` without touching
anything else.

### Linux / macOS

`./build-all.sh --install` (needs Gradle 8 + JDK 21).

### In game

Press **RIGHT SHIFT** to open the ClickGUI, **RIGHT SHIFT** again (or **ESC**) to
close it, and type **`.help`** in the chat.

## 4. Using the client

| Action | Default |
|---|---|
| Open / close ClickGUI | **RIGHT SHIFT** |
| Close current window, then the ClickGUI | **ESC** |
| Bind a module | `.bind <module> <key>` or click *BIND* in the GUI |
| Toggle a module | `.toggle <module>` |
| Command prefix | `.` (dot) |

ClickGUI categories: **COMBAT, MOVEMENT, PLAYER, RENDER, WORLD, AUTOMATION,
EXPLORATION, MISC, CLIENT, CONFIGS**. Every category panel is searchable,
scrollable, resizable and remembers its position.

### Commands

```
.help .bind .toggle .modules .search .cfg .config .friend .enemy .theme .hud
.panic .client .reset .settings .profile .export .import .gui .font .color
.watermark .waypoint .debug .version .stats .reload .backup
```

`.cfg` sub commands: `list | save | load | delete | rename | export | import |
folder | reset | backup`.

### Config files

Everything lives in `<game dir>/NexusOmegaX/configs/`:

```
config-settings.json  profiles/<name>.json  hud.json  themes.json
waypoints.json        macros.json           friends.json
notifications.json    session.json          backups/<name>-<timestamp>.json
```

All files are plain UTF-8 JSON (the client ships its own JSON engine, no
external dependencies), so they are safe to edit, diff and share.

## 5. Theming

Palette: `#08080D` `#101018` `#181824` `#7C3AED` `#9333EA` `#A855F7` `#C084FC`.

Themes: **OMEGA PURPLE, DARK MATTER, CYBER BLUE, BLOOD RED, NEON GREEN,
PURE WHITE, CUSTOM RGB** (`.theme <name>`, or the CLIENT tab).

## 6. Security and ethics

The client is a legitimate mod and contains **no** malware:

* no token / password / session stealing, no reading of `launcher_accounts.json`;
* no telemetry, no phone-home, no hidden network calls;
* no remote code execution, no auto update that downloads code;
* no disabling of OS protections, no hidden process injection;
* installation goes through the **official Fabric installer** only — no
  patched or repackaged launcher binaries.

PvP oriented modules are meant for single player, training worlds and servers
whose rules allow them. Several modules are explicitly local only
(`CriticalsLocal`, `XRayLocal`, `SpeedLocal`, …) and only change what your own
client renders or what your own client sends for your own movement. Respect
every server you join.

## 7. License

MIT. Do not redistribute closed-source copies of other clients; every line here
was written for this project.
